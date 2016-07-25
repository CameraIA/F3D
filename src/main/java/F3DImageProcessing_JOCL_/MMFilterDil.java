package F3DImageProcessing_JOCL_;

import static java.lang.System.nanoTime;
import static java.lang.System.out;
import ij.ImageStack;

import java.awt.Component;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram;

import java.util.ArrayList;

import javax.swing.JPanel;
/**!
 * Implementation of the Dilation operation.
 * @author hari
 *
 */
class MMFilterDil extends JOCLFilter
{
	/**!
	 * Construct a new instance of the filter.
	 */
    @Override
    public JOCLFilter newInstance() {
		return new MMFilterDil();
	}
    
    /**!
     * Serialize to JSON string format.
     */
	public String toJSONString() {
		String result = "{ " 
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\"Mask\" : " + filterPanel.toJSONString() 
				+ " }";
			return result;
	}
	
	/**!
	 * Deserialize from JSON string format.
	 */
	public void fromJSONString(String str) {
		filterPanel.fromJSONString(str);
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
		info.useTempBuffer = true;
		info.overlapX = info.overlapY = info.overlapZ = overlapAmount();
		return info;
    }
	
	/**!
	 * Name of the filter. Used in GUI.
	 */
    public String getName()
    {
        return "MMFilterDil";
    }

    /**!
     * Compute overlap amount needed from the user selected information.
     * @return
     */
    public int overlapAmount() {
    	int overlapAmount = 0;
    	
        for(int i = 0; i < filterPanel.maskImages.size(); ++i)
            overlapAmount = Math.max(overlapAmount, 
            		filterPanel.maskImages.get(i).getSize());
        
        return overlapAmount;
    }

    CLProgram program;
    CLKernel kernel, kernel2;

    /**!
     * Setup the filter for execution
     */
    @Override
    public boolean loadKernel()
    {
    	String dilation_comperror = "";
        try {
            String filename = "/OpenCL/MMdil3D.cl";
           	program = clattr.context.createProgram(BilateralFilter.class.getResourceAsStream(filename)).build();
        }
        catch(Exception e){
            e.printStackTrace();
            System.out.println("KERNEL Failed to Compile");
            
            StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
            
            dilation_comperror = errors.toString()+"\n"+
            		"Message exception: "+e.getMessage()+"\n";
            
            monitor.setKeyValue("dilation.comperror", dilation_comperror);

            
            return false;
        }
        monitor.setKeyValue("dilation.comperror", dilation_comperror);
         
        kernel = program.createCLKernel("MMdil3DFilterInit");
        kernel2 = program.createCLKernel("MMdil3DFilter");

//        System.out.println("Kernel loaded!");
        
        return true;
    }

    /**!
     * run the kernel on a set of mask images. This usually entails variable structured elements.
     * @param maskImages
     * @return
     */
    public boolean runKernel(ArrayList<ImageStack> maskImages, int overlapAmount)
    {     
	    CLBuffer<ByteBuffer> structElem = null;
	
	    String dilation_allocerror = "";
		
	    int globalSize[] = {0, 0}, localSize[] = {0, 0};
		clattr.computeWorkingGroupSize(localSize, globalSize, new int[] {atts.width, atts.height, 1});

		/**!
		 * run kernel over all the mask images.
		 */
        for(int i = 0; i < maskImages.size(); ++i) 
        {
            ImageStack mask = maskImages.get(i);

            int[] size = new int [3];
            
            size[0] = mask.getWidth();
            size[1] = mask.getHeight();
            size[2] = mask.getSize();
            structElem = atts.getStructElement(clattr.context, mask);
//            out.println("Rank: " + clattr.rank + " Processing: " + i);

            int startOffset = 0;
            int endOffset = 0; 

            if(atts.overlap.get(index) > 0) {
                startOffset = (int)(atts.overlap.get(index)/2);
                endOffset = (int)(atts.overlap.get(index)/2);
                if(atts.sliceStart <= 0)
                    startOffset = 0;

                if(atts.sliceEnd >= atts.slices)
                    endOffset = 0;
            }
    
            /** 
             * set up first execution
             */
            if(i == 0) {
              kernel.setArg(0,clattr.inputBuffer)
	            .setArg(1,clattr.outputTmpBuffer)
	            .setArg(2,atts.width)
	            .setArg(3,atts.height)
	            .setArg(4,atts.maxSliceCount.get(index) + atts.overlap.get(index))
	            .setArg(5,structElem)
	            .setArg(6,size[0])
	            .setArg(7,size[1])
	            .setArg(8,size[2])
                .setArg(9,startOffset)
                .setArg(10,endOffset);

            } else {
            	/**!
            	 * > 1 masks
            	 * This is called when the output from the iteration needs to be the
            	 * input in the new filter.
            	 */
              CLBuffer<ByteBuffer> tmpBuffer1, tmpBuffer2;
            
              tmpBuffer1 = i % 2 != 0 ? clattr.outputTmpBuffer : clattr.outputBuffer;
              tmpBuffer2 = i % 2 == 0 ? clattr.outputTmpBuffer : clattr.outputBuffer;
              
              kernel2.setArg(0,clattr.inputBuffer)
	            .setArg(1,tmpBuffer1)
	            .setArg(2,tmpBuffer2)
	            .setArg(3,atts.width)
	            .setArg(4,atts.height)
	            .setArg(5,atts.maxSliceCount.get(index) + atts.overlap.get(index))
	            .setArg(6,structElem)
	            .setArg(7,size[0])
	            .setArg(8,size[1])
	            .setArg(9,size[2])
                .setArg(10,startOffset)
                .setArg(11,endOffset);
            }
            
            /**!
             * copy element to accelerator
             */
            clattr.queue.putWriteBuffer(structElem, true);
	        
            /**!
             * run the kernel.
             */
            try {
            	clattr.queue.put2DRangeKernel(i == 0 ? kernel : kernel2, 0, 0, 
                        globalSize[0], globalSize[1], 
                        localSize[0], localSize[1]);            	
            } catch (Exception e) {
            	
                StringWriter errors = new StringWriter();
    			e.printStackTrace(new PrintWriter(errors));
                
                dilation_allocerror = errors.toString()+"\n"+
                		"Message exception: "+e.getMessage()+"\n";
                
                monitor.setKeyValue("dilation.allocerror", dilation_allocerror);

            	return false;
            }
            
            structElem.release();
        }

        monitor.setKeyValue("dilation.allocerror", dilation_allocerror);
        
        /**!
         * swap buffers from input to output.
         */
        if(maskImages.size() % 2 != 0) {
            CLBuffer<ByteBuffer> tmpBuffer = clattr.outputTmpBuffer;
            clattr.outputTmpBuffer = clattr.outputBuffer;
            clattr.outputBuffer = tmpBuffer;
        }
        return true;
    }

    /**!
     * run the filter.
     */
	@Override
	public boolean runFilter()
	{
        for(int i = 0; i < filterPanel.maskImages.size(); ++i) {
            ImageStack mask = filterPanel.maskImages.get(i);

            if(!atts.isValidStructElement(mask)) {
               out.println("ERROR: Structure element size is too large...");
               return false;
            }
        }

		//out.println("dil dims: " + atts.width + " " + atts.height + " " + atts.slices + " " + atts.channels);
		
		long time = nanoTime();

        clattr.queue.putWriteBuffer(clattr.inputBuffer, false);
        clattr.queue.finish();

        runKernel(filterPanel.maskImages, overlapAmount());
       
        clattr.queue.putReadBuffer(clattr.outputBuffer, false);
        //clattr.outputTmpBuffer.release();
        clattr.queue.finish();
        
		time = nanoTime() - time;

//		System.out.println("kernel created in :" + ((double)time)/1000000.0);

		return true;
	}

	/**!
	 * Release filter resources.
	 */
    @Override
    public boolean releaseKernel() 
    {
	    if (!kernel.isReleased()) kernel.release();
        if (!kernel2.isReleased()) kernel2.release();
        return true;
    }

    /**!
     * Construct custom user interface.
     */
	@Override
	public Component getFilterWindowComponent() {
		JPanel panel = new JPanel();
        panel.add(filterPanel.setupInterface());
        return panel;
	}

	/**!
	 * Callback for processing user selection.
	 */
	@Override
	public void processFilterWindowComponent() {
		filterPanel.processFilterWindowComponent();
	}

}
