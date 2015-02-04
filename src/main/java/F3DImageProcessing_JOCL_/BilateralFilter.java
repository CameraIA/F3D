package F3DImageProcessing_JOCL_;

import static com.jogamp.opencl.CLMemory.Mem.READ_WRITE;
import static java.lang.Math.min;
import static java.lang.System.nanoTime;
import ij.IJ;

import java.awt.Component;
import java.awt.GridLayout;
import java.nio.FloatBuffer;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram;

class BilateralFilter implements JOCLFilter
{
	private int spatialRadius = 3;
	private int rangeRadius = 30;

    CLAttributes clattr;
    FilteringAttributes atts;
    int index;
    CLProgram program;
    CLKernel kernel;
    CLBuffer<FloatBuffer> spatialKernel;
    CLBuffer<FloatBuffer> rangeKernel;

	//"{spatial : 3, range : 512}"
	//BilateralFilter f = new BilateralFilter();
	//f.setSpatialRadius(3);
	//f.setRangeRadius(5);
	//f.setOptions("spatial=3, range=5");
	
	//run("BilateralFilter", "setOptions=spatial=3 range=5");

	public JOCLFilter newInstance() {
		return new BilateralFilter();
	}
	
	public String toString() {
		String result = "{ "
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\"spatialRadius\" : \"" + spatialRadius + "\" , "
				+ "\"rangeRadius\" : \"" + rangeRadius
				+ "\" }";
		return result;
	}
	
	public void fromString(String options) {
		///parse options
		
		JSONParser parser = new JSONParser();
		
		try {
			 
			Object objOptions = parser.parse(options);
			JSONObject jsonOptionsObject = (JSONObject) objOptions;
			
			spatialRadius = Integer.valueOf((String)jsonOptionsObject.get("spatialRadius"));
			rangeRadius = Integer.valueOf((String) jsonOptionsObject.get("rangeRadius"));
	 
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
	}

	public void setSpatialRadius(int sRadius) {
		spatialRadius = sRadius;
	}
	
	public void setRangeRadius(int rRadius) {
		rangeRadius = rRadius;
	}
	
	public int getSpatialRadius() {
		return spatialRadius;
	}
	
	public int getRangeRadius() {
		return rangeRadius;
	}
	
	public String getOptions() {
		String options = "{}";
		return options;
	}
	
    @Override
    public String getName() {
        return "BilateralFilter";
    }
    
    @Override
    public int overlapAmount() {
        return spatialRadius;
    }

	private CLBuffer<FloatBuffer> makeKernel(CLAttributes clattr, CLProgram program, FilteringAttributes atts, double r)
	{
		double radius = r + 1;

		int bufferSize = (int)radius*2-1;
		int localSize = min(clattr.device.getMaxWorkGroupSize(), clattr.minWorkGroup);
		int globalSize = atts.roundUp(localSize, bufferSize);

//		System.out.println("bufferSize: " + bufferSize + " globalSize: " + globalSize + " localSize: " + localSize);
		
		CLBuffer<FloatBuffer> buffer = clattr.context.createFloatBuffer(globalSize, READ_WRITE);

		CLKernel kernel = program.createCLKernel("makeKernel");

		kernel.putArg((float)radius)
		.putArg(buffer)
		.putArg(bufferSize);
		

		long time = nanoTime();
	
		clattr.queue.put1DRangeKernel(kernel, 0, globalSize, localSize)
		.putReadBuffer(buffer,false).finish();

		float total = 0;
		for (int i = 0; i < bufferSize; ++i)
			total += buffer.getBuffer().get(i);

        //buffer.getBuffer().position(0);

		CLKernel normalizeKernel = program.createCLKernel("normalizeKernel");

		normalizeKernel.putArg((float)total)
		.putArg(buffer)
		.putArg(bufferSize);

		clattr.queue.put1DRangeKernel(normalizeKernel, 0, globalSize, localSize)
		.putReadBuffer(buffer,false).finish();

		time = nanoTime() - time;

		//System.out.println("kernel created in :" + ((double)time)/1000000.0);

        //buffer.getBuffer().position(0);
        //for(int i = 0; i < bufferSize; ++i)
        //    System.out.println(buffer.getBuffer().get(i));
        //buffer.getBuffer().position(0);

        kernel.release();
        normalizeKernel.release();
		return buffer;
	}

	@Override
	public int getDimensions()
	{
		return 2;
	}

    @Override
    public void setAttributes(CLAttributes c, FilteringAttributes a, int idx)
    {
        clattr = c;
        atts = a;
        index = idx;
    }
    
	@Override
    public boolean loadKernel()
    {
        try {
            String filename = "/OpenCL/" + (atts.enableZorder ? "BilateralFilterZorder.cl" : 
                                                                "BilateralFiltering.cl");
            program = clattr.context.createProgram(BilateralFilter.class.getResourceAsStream(filename)).build();

        }
        catch(Exception e) {
            e.printStackTrace();
            System.out.println("KERNEL Failed to Compile");
            return false;
        }
        
        
        // TALITA
//        if(clattr.outputTmpBuffer == null)
//		    clattr.outputTmpBuffer = clattr.context.createByteBuffer(clattr.inputBuffer.getBuffer().capacity(), READ_WRITE);
        //
        
		spatialKernel = makeKernel(clattr, program, atts, spatialRadius);
		rangeKernel = makeKernel(clattr, program, atts, rangeRadius);

		kernel = program.createCLKernel("BilateralFilter");


		
        return true;
    }

	@Override
	public boolean runFilter()
	{
//        System.out.println(atts.width + " " + atts.height + " " + atts.maxSliceCount.get(index) + " " + spatialRadius + " " + rangeRadius);
        
        kernel.setArg(0,clattr.inputBuffer)
		.setArg(1,clattr.outputBuffer)
		.setArg(2,atts.width)
		.setArg(3,atts.height)
		.setArg(4,atts.maxSliceCount.get(index))
		.setArg(5,spatialKernel)
		.setArg(6,(int)(spatialRadius+1)*2-1)
		.setArg(7,rangeKernel)
		.setArg(8,(int)(rangeRadius+1)*2-1);

        if(atts.enableZorder) {
            kernel.setArg(9, clattr.zorder.zIbits)
                  .setArg(10, clattr.zorder.zJbits)
                  .setArg(11, clattr.zorder.zKbits);
        }
		//write out results..
		//for some reason jogamp on fiji does not have 3DRangeKernel call..
		//clattr.queue.putWriteBuffer(clattr.inputBuffer, false);
		clattr.queue.putWriteBuffer(clattr.inputBuffer, true); //Hari version
        clattr.queue.put2DRangeKernel(kernel, 0, 0, 
                                        clattr.globalSize[0], clattr.globalSize[1], 
                                        clattr.localSize[0], clattr.localSize[1]);

		//clattr.queue.putReadBuffer(clattr.outputBuffer, false).finish();
		clattr.queue.putReadBuffer(clattr.outputBuffer, true).finish(); //Hari version
		
		
		
        return true;
	}

    @Override
    public boolean releaseKernel() {
    	if (!spatialKernel.isReleased()) spatialKernel.release();
        if (!rangeKernel.isReleased()) rangeKernel.release();
        if (!kernel.isReleased()) kernel.release();
        //if (!program.isReleased()) program.release();
        return true;
    }

	@Override
	public Component getFilterWindowComponent() {
		
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(2, 2, 0, 0));
		
		Integer min = new Integer(0);
		Integer max = new Integer(1000);
		Integer step = new Integer(1);
	
		JLabel rangeLabel = new JLabel("Range Radius: ");
		JSpinner rangeSpinner = new JSpinner(new SpinnerNumberModel(new Integer(rangeRadius), min, max, step));

		JLabel spatialLabel = new JLabel("Spatial Radius: ");
		JSpinner spatialSpinner = new JSpinner(new SpinnerNumberModel(new Integer(spatialRadius), min, max, step));
		
		panel.add(rangeLabel);
		panel.add(rangeSpinner);
		
		panel.add(spatialLabel);
		panel.add(spatialSpinner);
		
		rangeSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				JSpinner spinner = (JSpinner) e.getSource();
				rangeRadius = (Integer)spinner.getValue();
				//System.out.println("--X" + rangeRadius);
			}
		});

		spatialSpinner.addChangeListener(new ChangeListener() {			
			@Override
			public void stateChanged(ChangeEvent e) {
				JSpinner spinner = (JSpinner) e.getSource();
				spatialRadius = (Integer)spinner.getValue();
				//System.out.println("-->" + spatialRadius);
			}
		});
	
		return panel;
	}

	@Override
	public void processFilterWindowComponent() {
		
	}
}
