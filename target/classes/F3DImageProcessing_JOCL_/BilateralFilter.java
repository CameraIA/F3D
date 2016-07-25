package F3DImageProcessing_JOCL_;

import static com.jogamp.opencl.CLMemory.Mem.READ_WRITE;
import static java.lang.Math.min;
import static java.lang.System.nanoTime;
import java.awt.Component;
import java.awt.GridLayout;
import java.io.PrintWriter;
import java.io.StringWriter;
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

/**
 * BilateralFilter class 
 */
class BilateralFilter extends JOCLFilter
{
	private int spatialRadius = 3;
	private int rangeRadius = 30;

    CLProgram program;
    CLKernel kernel;
    CLBuffer<FloatBuffer> spatialKernel;
    CLBuffer<FloatBuffer> rangeKernel;

	public JOCLFilter newInstance() {
		return new BilateralFilter();
	}
	
    /**
     * Creates JSON string of filter to be used for scripting mode (recording in ImageJ)
     */
	public String toJSONString() {
		String result = "{ "
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\"spatialRadius\" : \"" + spatialRadius + "\" , "
				+ "\"rangeRadius\" : \"" + rangeRadius
				+ "\" }";
		return result;
	}
	
    /**
     * Creates filter from JSON string
     * @param options JSON string containing parameters for the filter
     */
	public void fromJSONString(String options) {	
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
    /**!
     * set the spatial radius for the bilateral filter.
     * @param sRadius - radius size
     */
	public void setSpatialRadius(int sRadius) {
		spatialRadius = sRadius;
	}
	
	/**!
	 * set the range radius for the bilateral filter
	 * @param rRadius - radius size
	 */
	public void setRangeRadius(int rRadius) {
		rangeRadius = rRadius;
	}
	
	/**!
	 * @return spatial radius
	 */
	public int getSpatialRadius() {
		return spatialRadius;
	}
	/**!
	 * 
	 * @return range radius
	 */
	public int getRangeRadius() {
		return rangeRadius;
	}
	
	/**!
	 * 
	 * @return options associated with this filter as a string.
	 */
	public String getOptions() {
		String options = "{}";
		return options;
	}
	
	/**!
	 * Get information about this filter. 
	 * Information includes name, storage type, 3D overlap.
	 */
	@Override
	public FilterInfo getInfo() {
		FilterInfo info = new FilterInfo();
		
		info.name = getName();
		info.memtype = JOCLFilter.Type.Byte;
		info.overlapX = spatialRadius;
		info.overlapY = spatialRadius;
		info.overlapZ = spatialRadius;
		
		return info;
	}
	
	/**!
	 * Name of the filter. Used in GUI.
	 */
	public String getName() {
		return "BilateralFilter";
	}

    /**
     * Makes filter kernel for a specific radius value
     * @param  r radius value
     * @return float buffer with kernel data.
     */
    private CLBuffer<FloatBuffer> makeKernel(double r)
	{
		double radius = r + 1;

		int minWorkingGroup = 256;
		///TODO: verify if this logic is still necessary.
		if(clattr.device.getType().toString().equals("CPU")) minWorkingGroup = 64;
		
		int bufferSize = (int)radius*2-1;
		int localSize = min(clattr.device.getMaxWorkGroupSize(), minWorkingGroup);
		int globalSize = clattr.roundUp(localSize, bufferSize);
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

        kernel.release();
        normalizeKernel.release();
		return buffer;
	}

    /**!
     * Run the spatial and range kernels to compute weights 
     * and setup the Bilateral Filter kernel.
     */
	@Override
	public boolean loadKernel()
	{
		String bilateral_comperror="";
        try {
        	/**!
        	 * Load the OpenCL kernel file.
        	 * TODO: possibly replace this with string version.
        	 */
            String filename = "/OpenCL/BilateralFiltering.cl";
            program = clattr.context.createProgram(BilateralFilter.class.getResourceAsStream(filename)).build();
        	
//        	String kernel = HelperFunctionKernels + 
//					   		BilateralFilterKernel;
//        	
//        	program = clattr.context.createProgram(kernel);
//        	program.build();
        }
        catch(Exception e) {
            e.printStackTrace();
            System.out.println("KERNEL Failed to Compile");
            
            StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
            
            bilateral_comperror = errors.toString()+"\n"+
            		"Message exception: "+e.getMessage()+"\n";
            monitor.setKeyValue("bilateral.comperror", bilateral_comperror);
                        
            return false;
        }
        monitor.setKeyValue("bilateral.comperror", bilateral_comperror);

        /**!
         * Make and invoke spatial and range kernels.
         * return buffer data associated with kernel execution.
         */
        spatialKernel = makeKernel(spatialRadius);
		rangeKernel = makeKernel(rangeRadius);

		kernel = program.createCLKernel("BilateralFilter");
        return true;
	}
	
    /**
     * Main run method 
     */
	@Override
	public boolean runFilter()
	{
		String bilateral_allocerror="";
		
		int globalSize[] = {0, 0}, localSize[] = {0, 0};
		clattr.computeWorkingGroupSize(localSize, globalSize, new int[] {atts.width, atts.height, 1});
		
		try {
		
			/**!
			 * write input data to the accelerator
			 */
			clattr.queue.putWriteBuffer(clattr.inputBuffer, true); //Hari version
        
			/**!
			 * setup kernel with arguments.
			 */
			//System.out.println(" " + atts.maxSliceCount.get(index) + " " + atts.overlap.get(index));
			kernel.setArg(0,clattr.inputBuffer)
			.setArg(1,clattr.outputBuffer)
			.setArg(2,atts.width)
			.setArg(3,atts.height)
			.setArg(4,atts.maxSliceCount.get(index) + getInfo().overlapZ)
			//.setArg(4,atts.maxSliceCount.get(index))
			.setArg(5,spatialKernel)
			.setArg(6,(int)(spatialRadius+1)*2-1)
			.setArg(7,rangeKernel)
			.setArg(8,(int)(rangeRadius+1)*2-1);

			/**!
			 * create kernel work group which includes local and global
			 * work sizes.
			 */
			clattr.queue.put2DRangeKernel(kernel, 0, 0, 
					globalSize[0], globalSize[1], 
					localSize[0], localSize[1]);

		} catch (Exception e) {
			 
		    StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
            
            bilateral_allocerror = errors.toString()+"\n"+
            		"Message exception: "+e.getMessage()+"\n";
            
            monitor.setKeyValue("bilateral.allocerror", bilateral_allocerror);
            return false;
			
		}
		
		/**!
		 * Write out results.
		 */
        monitor.setKeyValue("bilateral.allocerror", bilateral_allocerror);
		clattr.queue.putReadBuffer(clattr.outputBuffer, true).finish(); //Hari version
		
        return true;
	}

	/**!
	 * Release memory and kernels held by this filter.
	 */
    @Override
    public boolean releaseKernel() {
    	if (!spatialKernel.isReleased()) spatialKernel.release();
        if (!rangeKernel.isReleased()) rangeKernel.release();
        if (!kernel.isReleased()) kernel.release();
        //if (!program.isReleased()) program.release();
        return true;
    }

    /**
     * Creates custom interface for the filter 
     * @return Window Component
     */
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

	/**!
	 * calls this function in case user interface needs to process
	 * when Filter is activated.
	 */
	public void processFilterWindowComponent() {		
	}

	
	/**!
	 * TODO: use in future (instead of file version)
	 */
	final String BilateralFilterKernel =
			
"	float dynamicKernel(float radius, int index)\n" +
"	{\n" +
"	    float x = (index + 1 - radius) / (radius * 2) / 0.2;\n" +
"	    return (float)exp(-0.5 * x * x);\n" +
"	}\n" +

"	kernel void makeKernel(float radius, global float* output, const int output_size)\n" +
"	{\n" +
"	    size_t i = get_global_id(0);\n" +
"	    if(i >= output_size) return;\n" +
"	    output[i] = dynamicKernel(radius,i);\n" +
"	}\n" +

"	kernel void normalizeKernel(float total, global float* output, const int output_size)\n" +
"	{\n" +
"		size_t i = get_global_id(0);\n" +
"	    if(i >=  output_size) return;\n" +
"	     if (total <= 0.0)\n" +
"	        output[i] = 1.0f / output_size;\n" +
"	     else if (total != 1.0)\n" +
"	        output[i] /= total;\n" +
"	}\n" +

"	void BilateralFilter3D(const int3 pos, const int3 sizes, \n" +
"            global const uchar* inputBuffer, global uchar* outputBuffer, \n" +
"            global const float* spatialKernel, const int spatialSize,\n" +
"            global const float* rangeKernel, const int rangeSize)\n" +
"	{\n" +
"		int v0 =  getValue(inputBuffer, pos, sizes);\n" +

"		int sc = (int)spatialSize / 2;\n" +
"		int rc = (int)rangeSize / 2;\n" +

"		float v = 0;\n" +
"		float total = 0;\n" +

"		for (int n = 0; n < spatialSize; ++n)\n" +
"		{\n" +
"			for (int m = 0; m < spatialSize; ++m)\n" +
"			{\n" +
"				for (int k = 0; k < spatialSize; ++k)\n" +
"				{\n" +
" 					int3 pos2 = { pos.x + n - sc, pos.y + m - sc, pos.z + k - sc };\n" +
" 					int v1 = getValue(inputBuffer, pos2, sizes);\n" +

" 					if(v1 < 0) continue;\n" +
" 					if(abs(v1-v0) > rc) continue;\n" +

" 					float w = spatialKernel[m] * spatialKernel[n] * rangeKernel[v1 - v0 + rc];\n" +
" 					v += v1 * w;\n" +
" 					total += w;\n" +
"    			}\n" +
"   		}\n" +
"  		}\n" +
"		setValue(outputBuffer, pos, sizes, (int)(v/total));\n" +
"	}\n" +

"kernel void BilateralFilter(global const uchar* inputBuffer, global uchar* outputBuffer,\n" +  
"             				 const int imageWidth, const int imageHeight, const int imageDepth,\n" +
"             				 global const float* spatialKernel, const int spatialSize,\n" +
"             				 global const float* rangeKernel, const int rangeSize)\n" +
"{\n" +

"	const int3 sizes = { imageWidth, imageHeight, imageDepth };\n" +
"	int3 pos = { get_global_id(0), get_global_id(1), 0 };\n" +

"	if(isOutsideBounds(pos, sizes)) return;\n" +

"	for(int i = 0; i < imageDepth; ++i)\n" +
"	{\n" +
"		int3 pos = { get_global_id(0), get_global_id(1), i };\n" +
"		BilateralFilter3D(pos, sizes,\n" + 
"               inputBuffer, outputBuffer,\n" +     
"               spatialKernel, spatialSize,\n" + 
"               rangeKernel, rangeSize);\n" +
"  	}\n" +    
"}\n";
}
