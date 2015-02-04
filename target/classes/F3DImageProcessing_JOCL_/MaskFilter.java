package F3DImageProcessing_JOCL_;

import static java.lang.System.nanoTime;
import ij.IJ;
import ij.ImageStack;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.nio.ByteBuffer;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

class MaskFilter implements JOCLFilter
{
	FilteringAttributes.FilterPanel filterPanel =
			new FilteringAttributes.FilterPanel();
	
	String[] maskChoices = { "mask3D" };
    String selectedMaskChoice = "";
    int index;
    
    @Override
    public JOCLFilter newInstance() {
		return new MaskFilter();
	}
    
	public String toString() {
		String result = "{ " 
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\"selectedMaskChoice\" : \"" + selectedMaskChoice  + "\" , "
				+ "\"Mask\" : " + filterPanel.toString()
				+ " }";
//		System.out.println(result);
		return result;
	}
	
	public void fromString(String options) {
		///parse options
		
		JSONParser parser = new JSONParser();
		
		try {
			 
			Object objOptions = parser.parse(options);
			JSONObject jsonOptionsObject = (JSONObject) objOptions;
			
			selectedMaskChoice = (String)jsonOptionsObject.get("selectedMaskChoice");
	 
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		filterPanel.fromString(options);
		
	}
	
    @Override
    public String getName()
    {
        return "MaskFilter";
    }

    @Override
    public int overlapAmount() {
        return 0;
    }

	public int getDimensions()
	{
		return 1;
	}

    CLAttributes clattr;
    FilteringAttributes atts;
    CLBuffer<ByteBuffer> maskBuffer = null;
    CLProgram program;
    CLKernel kernel;
    
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
//        	program = clattr.context.createProgram(MedianFilter.class.getResourceAsStream("/OpenCL/Mask3D.cl")).build();
        	program = clattr.context.createProgram(MaskFilter.class.getResourceAsStream("/OpenCL/Mask3D.cl")).build();
        }
        catch(Exception e){
            return false;
        }
        
	    kernel = program.createCLKernel(selectedMaskChoice);
        return true;
    }

	@Override
	public boolean runFilter()
	{
		ImageStack mask = filterPanel.maskImages.get(0);   // maskImages NULL??
		int[] size = new int [3];
		size[0] = mask.getWidth();
		size[1] = mask.getHeight();
		size[2] = mask.getSize();

        if(atts.width*atts.height*atts.slices != size[0]*size[1]*size[2])
        {
            System.out.println("Mask not equal Original image");
            return false;
        }

        //out.println("dims: " + atts.width + " " + atts.height + " " + atts.slices + " " + atts.channels);

        long time = nanoTime();

        maskBuffer = atts.getStructElement(clattr.context, mask, clattr.globalSize[0]);
    
        kernel.setArg(0,clattr.inputBuffer)
        .setArg(1,maskBuffer)
        .setArg(2,clattr.outputBuffer)
        .setArg(3,atts.width)
        .setArg(4,atts.height)
        .setArg(5,atts.maxSliceCount.get(index));
        
//    	System.out.println(selectedMaskChoice);
		
        //write out results..
        //for some reason jogamp on fiji does not have 3DRangeKernel call..
        clattr.queue.putWriteBuffer(clattr.inputBuffer, true);
        clattr.queue.finish();
        
        clattr.queue.putWriteBuffer(maskBuffer, true);
        clattr.queue.putWriteBuffer(clattr.outputBuffer, true); // ???
        clattr.queue.put1DRangeKernel(kernel, 0, 
	            clattr.globalSize[0], 
	            clattr.localSize[0]);
        clattr.queue.putReadBuffer(clattr.outputBuffer, true);
        
        clattr.queue.finish();

        time = nanoTime() - time;

        //System.out.println("kernel created in :" + ((double)time)/1000000.0);
        
		return true;
	}

    @Override
    public boolean releaseKernel()
    {
        if(maskBuffer != null) maskBuffer.release();
        if (!kernel.isReleased()) kernel.release();
        return true;
    }

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Component getFilterWindowComponent() {
		
		        
        JPanel panel = new JPanel();
        GridLayout layout = new GridLayout(2, 2, 0, 0);
        panel.setLayout(layout);
        
        //fiji complains when I use JComboBox<String>
        JLabel mask = new JLabel("Mask Choices:");
        //@SuppressWarnings({ "rawtypes", "unchecked" }) 
		JComboBox mcb = new JComboBox(maskChoices);
        
        if(selectedMaskChoice.equals(""))
        	selectedMaskChoice = maskChoices[0];
        
        mcb.setSelectedItem(selectedMaskChoice);
             
        mcb.addItemListener(new ItemListener() {
			
			@Override
			public void itemStateChanged(ItemEvent e) {
				String choice = (String) e.getItem();
				if(e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
//					System.out.println(choice);
					selectedMaskChoice = choice;
				}
			}
		});
        
        JPanel p = new JPanel();
        p.add(mask);
        p.add(mcb);
        
        panel.add(p);
        panel.add(filterPanel.setupInterface());
        
        return panel;
	}

	@Override
	public void processFilterWindowComponent() {
		filterPanel.processFilterWindowComponent();
	}
}
