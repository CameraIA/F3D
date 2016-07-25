package F3DImageProcessing_JOCL_;

import static java.lang.System.nanoTime;
import static java.lang.System.out;
import ij.ImageStack;

import java.awt.Component;
import java.nio.ByteBuffer;

import F3DImageProcessing_JOCL_.MMFilterClo;
import com.jogamp.opencl.CLBuffer;

import javax.swing.JPanel;

/**!
 * Implementation of the Closing operation.
 * This is implemented with a combination of executing the Dilation Kernel then an Erosion Kernel.
 * @author hari
 *
 */
class MMFilterClo extends JOCLFilter
{	
	/**!
	 * Create new instance of the filter.
	 */
	@Override
    public JOCLFilter newInstance() {
		return new MMFilterClo();
	}
	
	/**!
	 * Serialize to JSON format string
	 * @return JSON string
	 */
	public String toJSONString() {
		String result = "{ "
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\"Mask\" : " + filterPanel.toJSONString()  
				+ " }";
			return result;
	}
	
	/**!
	 * DeSerialize from JSON format string
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
		///TODO: return X and Y..
		info.overlapX = info.overlapY = info.overlapZ = overlapAmount();
		return info;
    }
	
	/**!
	 * Name of the filter. Used in GUI.
	 */
    public String getName()
    {
        return "MMFilterClo";
    }

    /**!
     * Compute amount of overlap. This depends on which masks were selected
     * and the size of mask.
     * @return size of overlapAmount
     */
    private int overlapAmount() {
    	int overlapAmount = 0;
    	
    	for(int i = 0; i < filterPanel.maskImages.size(); ++i)
            overlapAmount = Math.max(overlapAmount, 
            						 filterPanel.maskImages.get(i).getSize());
        
        return overlapAmount;
    }

    MMFilterEro erosion;
    MMFilterDil dilation;

    /**!
     * Set up the filters needed for a close operation.
     */
    @Override
    public boolean loadKernel()
    {
    	erosion = new MMFilterEro();
        dilation = new MMFilterDil();

        erosion.setAttributes(clattr, atts, monitor, index);
        if (!erosion.loadKernel())
        	return false;
        
        dilation.setAttributes(clattr, atts, monitor, index);      
        if (!dilation.loadKernel())
        	return false;    
        return true;
    }

    /**!
     * Run the Filter. Which involves invoking the dilation filter followed
     * by an erosion filter.
     */
	@Override
	public boolean runFilter()
	{
		/**!
		 * Ensure masks fall within a safe maximum.
		 */
        for(int i = 0; i < filterPanel.maskImages.size(); ++i) {
            ImageStack mask = filterPanel.maskImages.get(i);

            if(!atts.isValidStructElement(mask)) {
               out.println("ERROR: Structure element size is too large...");
               return false;
            }
        }

		//out.println("dims: " + atts.width + " " + atts.height + " " + atts.slices + " " + atts.channels);

		long time = nanoTime();

		/**!
		 * Ensure data is available on the GPU.
		 */
	    clattr.queue.putWriteBuffer(clattr.inputBuffer, false);
        clattr.queue.finish();

        /**!
         * run dilation kernel
         */
        if (!dilation.runKernel(filterPanel.maskImages, overlapAmount()))
        	return false;

		//swap input and output..
        /**!
         * Swap results to make output back to input.
         */
        CLBuffer<ByteBuffer> tmpBuffer = clattr.inputBuffer;
        clattr.inputBuffer = clattr.outputBuffer;
        clattr.outputBuffer = tmpBuffer;
        
        /**!
         * run erosion kernel
         */
        if (!erosion.runKernel(filterPanel.maskImages, overlapAmount()))
        	return false;
        		
        clattr.queue.putReadBuffer(clattr.outputBuffer, false);
        clattr.queue.finish();

        //clattr.outputTmpBuffer.release();

		time = nanoTime() - time;

		//System.out.println("kernel created in :" + ((double)time)/1000000.0);
		
		return true;
	}

	/**!
	 * Release filter resources.
	 */
    @Override
    public boolean releaseKernel() {
        erosion.releaseKernel();
        dilation.releaseKernel();
//        clattr.outputTmpBuffer.release();
        return true;
    }

    /**!
     * Create custom user interface.
     */
	@Override
	public Component getFilterWindowComponent()	{
		JPanel panel = new JPanel();
		panel.add(filterPanel.setupInterface());
		return panel;
	}

	/**!
	 * Process any information for selection of the interface.
	 */
	@Override
	public void processFilterWindowComponent() {
		filterPanel.processFilterWindowComponent();
	}

}
