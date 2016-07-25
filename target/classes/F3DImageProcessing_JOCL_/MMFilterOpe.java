package F3DImageProcessing_JOCL_;

import static java.lang.System.nanoTime;
import static java.lang.System.out;
import ij.ImageStack;

import java.awt.Component;
import java.nio.ByteBuffer;

import F3DImageProcessing_JOCL_.MMFilterOpe;

import com.jogamp.opencl.CLBuffer;

import javax.swing.JPanel;

/**!
 * Implementation of the Opening operation.
 * This is implemented with a combination of executing the Erosion Kernel then a Dilation Kernel.
 * @author hari
 *
 */
class MMFilterOpe extends JOCLFilter
{	
	/**!
	 * Construct new instance of the filter.
	 */
	@Override
    public JOCLFilter newInstance() {
		return new MMFilterOpe();
	}
	
	/**!
	 * Serialize from JSON string format.
	 */
	public String toJSONString() {
		String result = "{ "
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\"Mask\" : " + filterPanel.toJSONString()  
				+ " }";
			return result;
	}
	
	/**!
	 * DeSerialize from JSON string format.
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
		info.overlapX = info.overlapY = info.overlapZ = overlapAmount();
		return info;
    }
	
	/**!
	 * Name of the filter. Used in GUI.
	 */
    public String getName()
    {
        return "MMFilterOpe";
    }

    /**!
     * Comput overlap amount from user selected mask images.
     * @return
     */
    public int overlapAmount() {
    	int overlapAmount = 0;
    	
    	for(int i = 0; i < filterPanel.maskImages.size(); ++i)
            overlapAmount = Math.max(overlapAmount, 
            						 filterPanel.maskImages.get(i).getSize());
        
    	return overlapAmount;
    }

    MMFilterDil dilation;
    MMFilterEro erosion;

    /**!
     * setup Erosion & Dilation kernels
     */
    @Override
    public boolean loadKernel() 
    {
        dilation = new MMFilterDil();
        erosion = new MMFilterEro();
        
        dilation.setAttributes(clattr, atts, monitor, index);            
        if (!dilation.loadKernel())
        	return false;

        erosion.setAttributes(clattr, atts, monitor, index);            
        if (!erosion.loadKernel())
        	return false; 
        return true;              
    }

    /**!
     * Run the filter.
     */
	@Override
	public boolean runFilter()
	{
		/**!
		 * Test to ensure mask images fall within some size.
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

        clattr.queue.putWriteBuffer(clattr.inputBuffer, false);
        clattr.queue.finish();

        /**!
         * Call Erosion kernels.
         */
        if (!erosion.runKernel(filterPanel.maskImages, overlapAmount()))
        	return false;
        
		//swap input and output..
        CLBuffer<ByteBuffer> tmpBuffer = clattr.inputBuffer;
        clattr.inputBuffer = clattr.outputBuffer;
        clattr.outputBuffer = tmpBuffer;
        
        /**!
         * Call Dilation kernels.
         */
        if (!dilation.runKernel(filterPanel.maskImages, overlapAmount()))
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
        dilation.releaseKernel();
        erosion.releaseKernel();
        return true;
    }

    /**!
     * Create custom user interface.
     */
    @Override
	public Component getFilterWindowComponent() {
		JPanel panel = new JPanel();
        panel.add(filterPanel.setupInterface());
        return panel;
	}

    /**!
     * Callback to process user selection.
     */
	@Override
	public void processFilterWindowComponent() {
		filterPanel.processFilterWindowComponent();
	}
}
