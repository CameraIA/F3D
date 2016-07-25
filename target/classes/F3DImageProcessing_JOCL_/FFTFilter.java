package F3DImageProcessing_JOCL_;

import static java.lang.System.nanoTime;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
/**!
 * Filter that provides FFT operations.
 * @author hari
 *
 */
class FFTFilter extends JOCLFilter
{
	enum FFTChoice { Forward, Inverse };
	
    CLProgram program;
    CLKernel kernel;

	FFTChoice selectedFFTChoice = FFTChoice.Forward;
    
	/**!
	 * Create a new instance of FFT Filter and return it.
	 */
    @Override
    public JOCLFilter newInstance() {
		return new FFTFilter();
	}
    
    /**!
     * Serialize to JSON string format
     */
	public String toJSONString() {
		String result = "{ " 
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\"fftChoice\" : \"" + selectedFFTChoice  + "\" , "
				+ " }";
		return result;
	}
	
	/**!
	 * DeSerialize from JSON string format.
	 */
	public void fromJSONString(String options) {
		///parse options
		
		JSONParser parser = new JSONParser();
		
		try {
			 
			Object objOptions = parser.parse(options);
			JSONObject jsonOptionsObject = (JSONObject) objOptions;
			
			selectedFFTChoice = FFTChoice.valueOf((String)jsonOptionsObject.get("fftChoice"));
	 
		} catch (ParseException e) {
			e.printStackTrace();
		}
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
		info.memtype = JOCLFilter.Type.Float;
		info.useTempBuffer = true;
		info.overlapX = info.overlapY = info.overlapZ = 0;
		
        return info;
    }
	
	/**!
	 * Name of the filter. Used in GUI.
	 */
	public String getName() {
		return "FFTFilter";
	}
    
    /**!
     * Load and build FFT Filter. 
     */
    @Override
    public boolean loadKernel()
    {
    	String mask_comperror = "";
        try {

        	String filename = "/OpenCL/FFTFilter.cl";
        	program = clattr.context.createProgram(FFTFilter.class.getResourceAsStream(filename));
        	program.build();
        }
        catch(Exception e){
        	
        	System.out.println(program.getBuildLog());
            
        	StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
            
            mask_comperror = errors.toString()+"\n"+
            		"Message exception: "+e.getMessage()+"\n";
                    	
            return false;
        }
        monitor.setKeyValue("mask.comperror", mask_comperror);
        kernel = program.createCLKernel("FFTFilter");
        return true;
    }

    /**!
     * Setup Filter for execution.
     */
	@Override
	public boolean runFilter()
	{
		String mask_allocerror = "";
		/**!
		 * Select Between Forward & Inverse FFT operation. 
		 */
		int direction = selectedFFTChoice == FFTChoice.Forward ? 1 : -1;
		
		long time = nanoTime();

        try {
        	/// write data to device..
            clattr.queue.putWriteBuffer(clattr.inputBuffer, true);
                        
            /**!
             * FFT in X direction
             */
            /// FFT X direction..
            int globalSize[] = {0, 0}, localSize[] = {0, 0};
    		clattr.computeWorkingGroupSize(localSize, globalSize, new int[] {atts.height, atts.slices, 1});

    		/**!
    		 * Set up kernel
    		 */
    		kernel.setArg(0,clattr.inputBuffer)
            .setArg(1,clattr.outputBuffer)
            .setArg(2,clattr.outputTmpBuffer)
            .setArg(3,direction)
            .setArg(4,atts.width)
            .setArg(5,atts.height)
            .setArg(6,atts.maxSliceCount.get(index))
            .setArg(7, 0);
    	    
    		/**!
    		 * Set up 2D Kernel.
    		 */
    		clattr.queue.put2DRangeKernel(kernel, 0, 0, 
    	            globalSize[0], globalSize[1], 
    	            localSize[0], localSize[1]);

    		/**!
             * FFT in Y direction
             */
    		clattr.computeWorkingGroupSize(localSize, globalSize, new int[] {atts.width, atts.slices, 1});

    		/**!
    		 * Set up kernel parameters
    		 */
        	kernel.setArg(0,clattr.inputBuffer)
            .setArg(1,clattr.outputBuffer)
            .setArg(2,clattr.outputTmpBuffer)
            .setArg(3,direction)
            .setArg(4,atts.width)
            .setArg(5,atts.height)
            .setArg(6,atts.maxSliceCount.get(index))
            .setArg(7, 1);
    		
    		clattr.queue.put2DRangeKernel(kernel, 0, 0, 
    	            globalSize[0], globalSize[1], 
    	            localSize[0], localSize[1]);
        
    		/**!
             * FFT in Z direction
             */
            clattr.computeWorkingGroupSize(localSize, globalSize, new int[] {atts.width, atts.height, 1});

            /**!
             * set kernel parameters
             */
    		kernel.setArg(0,clattr.inputBuffer)
            .setArg(1,clattr.outputBuffer)
            .setArg(2,clattr.outputTmpBuffer)
            .setArg(3,direction)
            .setArg(4,atts.width)
            .setArg(5,atts.height)
            .setArg(6,atts.maxSliceCount.get(index))
            .setArg(7, 2);
    		
    		clattr.queue.put2DRangeKernel(kernel, 0, 0, 
    	            globalSize[0], globalSize[1], 
    	            localSize[0], localSize[1]);
        
    		
        } catch (Exception e) {
            
        	e.printStackTrace();
        	
        	StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
            
            mask_allocerror = errors.toString()+"\n"+
            		"Message exception: "+e.getMessage()+"\n";            

        }
        
        /**!
         * Write output buffer.
         */
        monitor.setKeyValue("mask.allocerror", mask_allocerror);
        clattr.queue.putReadBuffer(clattr.outputBuffer, true);
        
        clattr.queue.finish();

        time = nanoTime() - time;

        //System.out.println("kernel created in :" + ((double)time)/1000000.0);
        
		return true;
	}

	/**!
	 * Release resources
	 */
    @Override
    public boolean releaseKernel()
    {
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
        JLabel mask = new JLabel("FFT Choices:");
        //@SuppressWarnings({ "rawtypes", "unchecked" }) 
		JComboBox mcb = new JComboBox(new String [] {
											FFTChoice.values()[0].toString(), 
											FFTChoice.values()[1].toString()});
							        
        mcb.setSelectedItem(selectedFFTChoice);
             
        mcb.addItemListener(new ItemListener() {
			
			@Override
			public void itemStateChanged(ItemEvent e) {
				String choice = (String) e.getItem();
				if(e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
					selectedFFTChoice = FFTChoice.valueOf(choice);
				}
			}
		});
        
        panel.add(mask);
        panel.add(mcb);
        
        return panel;
	}

	/**!
	 * calls this function in case user interface needs to process
	 * when Filter is activated.
	 */
	@Override
	public void processFilterWindowComponent() {
	}
}
