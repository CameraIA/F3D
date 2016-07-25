package F3DImageProcessing_JOCL_;

import static java.lang.System.nanoTime;
import ij.ImageStack;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.PrintWriter;
import java.io.StringWriter;
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
/**!
 * Filter to perform mask operations in OpenCL
 * @author hari
 */
class MaskFilter extends JOCLFilter
{
    CLBuffer<ByteBuffer> maskBuffer = null;
    CLProgram program;
    CLKernel kernel;

	String[] maskChoices = { "mask3D" };
    String selectedMaskChoice = "";
    
    /**!
     * Create new instance of Mask Filter.
     * @return new mask filter
     */
    @Override
    public JOCLFilter newInstance() {
		return new MaskFilter();
	}
    
    /**!
     * Serialize data to JSON string format
     */
	public String toJSONString() {
		String result = "{ " 
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\"selectedMaskChoice\" : \"" + selectedMaskChoice  + "\" , "
				+ "\"Mask\" : " + filterPanel.toJSONString()
				+ " }";
		return result;
	}
	
    /**!
     * DeSerialize data to JSON string format
     */
	public void fromJSONString(String options) {
		///parse options
		
		JSONParser parser = new JSONParser();
		
		try {
			 
			Object objOptions = parser.parse(options);
			JSONObject jsonOptionsObject = (JSONObject) objOptions;
			
			selectedMaskChoice = (String)jsonOptionsObject.get("selectedMaskChoice");
	 
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		filterPanel.fromJSONString(options);
	}
	
	/**!
	 * Get information about this filter. 
	 * Information includes name, storage type, 3D overlap.
	 */	
    @Override
    public FilterInfo getInfo()
    {
        FilterInfo info = new FilterInfo();
        info.name = getName();
        info.memtype = JOCLFilter.Type.Byte;
        info.overlapX = info.overlapY = info.overlapZ = 0;
        return info;
    }
    
	/**!
	 * Name of the filter. Used in GUI.
	 */
    public String getName() {
    	return "MaskFilter";
    }
    
    /**!
     * Load and build Mask Filter.
     */
    @Override
    public boolean loadKernel()
    {
    	String mask_comperror = "";
        try {
        	program = clattr.context.createProgram(MaskFilter.class.getResourceAsStream("/OpenCL/Mask3D.cl")).build();
        }
        catch(Exception e){
        	
            StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
            
            mask_comperror = errors.toString()+"\n"+
            		"Message exception: "+e.getMessage()+"\n";
                    	
            return false;
        }
        
        /**!
         * create kernel with chosen mask.
         */
        monitor.setKeyValue("mask.comperror", mask_comperror);
        kernel = program.createCLKernel(selectedMaskChoice);
        return true;
    }

    /**!
     * Run mask filter with mask chosen by user.
     */
	@Override
	public boolean runFilter()
	{
		String mask_allocerror = "";
		ImageStack mask = filterPanel.maskImages.get(0);   // maskImages NULL??
		int[] size = new int [3];
		size[0] = mask.getWidth();
		size[1] = mask.getHeight();
		size[2] = mask.getSize();

		/**
		 * error checking.
		 */
        if(atts.width*atts.height*atts.slices != size[0]*size[1]*size[2])
        {
            System.out.println("Mask not equal Original image");
            return false;
        }

        //out.println("dims: " + atts.width + " " + atts.height + " " + atts.slices + " " + atts.channels);

        long time = nanoTime();

        /**!
         * create mask buffer and setup parameters 
         * and working group.
         */
		int globalSize[] = {0}, localSize[] = {0};
		clattr.computeWorkingGroupSize(localSize, globalSize, 
							new int[] {atts.width, atts.height, 
									   (atts.maxSliceCount.get(index) + atts.overlap.get(index))});

		maskBuffer = atts.getStructElement(clattr.context, mask, globalSize[0]);
    
        kernel.setArg(0,clattr.inputBuffer)
        .setArg(1,maskBuffer)
        .setArg(2,clattr.outputBuffer)
        .setArg(3,atts.width)
        .setArg(4,atts.height)
        .setArg(5,atts.maxSliceCount.get(index) + atts.overlap.get(index));
        
		try {
			///write data to accelerator
        	clattr.queue.putWriteBuffer(clattr.inputBuffer, true);
            clattr.queue.finish();
            
            clattr.queue.putWriteBuffer(maskBuffer, true);
            clattr.queue.putWriteBuffer(clattr.outputBuffer, true); // ???
            
            //run kernel
            clattr.queue.put1DRangeKernel(kernel, 0, globalSize[0], localSize[0]);
        } catch (Exception e) {
            StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
            
            mask_allocerror = errors.toString()+"\n"+
            		"Message exception: "+e.getMessage()+"\n";            

        }
        
		/**!
		 * Write results back to CLBuffer
		 */
        monitor.setKeyValue("mask.allocerror", mask_allocerror);
        clattr.queue.putReadBuffer(clattr.outputBuffer, true);
        
        clattr.queue.finish();

        time = nanoTime() - time;

        //System.out.println("kernel created in :" + ((double)time)/1000000.0);
        
		return true;
	}

	/**!
	 * Release filter resources.
	 */
    @Override
    public boolean releaseKernel()
    {
        if(maskBuffer != null) maskBuffer.release();
        if (!kernel.isReleased()) kernel.release();
        return true;
    }

    /**!
     * Create custom user interface.
     */
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

	/**!
	 * Create template mask if needed or retrieve one from Fiji.
	 */
	@Override
	public void processFilterWindowComponent() {
		filterPanel.processFilterWindowComponent();
	}
}
