package F3DImageProcessing_JOCL_;

import static java.lang.System.nanoTime;

import java.awt.Button;
import java.awt.Component;
import java.awt.Panel;
import java.io.PrintWriter;
import java.io.StringWriter;

import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram;

/**!
 * MedianFilter implements the 3D median filter without sorting optimization - TODO: use quicksort
 * Implements a median computation from a one neighborhood.
 * mid point index of 1D = 1, 2D = 4, 3D = 13
 */

class MedianFilter extends JOCLFilter
{
	int index;
		
	/**!
	 * Create new instance of the filter.
	 */
    @Override
    public JOCLFilter newInstance() {
		return new MedianFilter();
	}
	
    /**!
     * Serialize to JSON string format
     */
    public String toJSONString() {
    	String result = "{ "
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\" }";
		return result;
	}
	
    /**!
     * DeSerialize to JSON string format
     */	
    public void fromJSONString(String options) {
		///parse options
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
    public String getName()
    {
        return "MedianFilter";
    }
    
    CLProgram program;
    CLKernel kernel;

    /**!
     * Build and load Median filter kernel
     */
    @Override
    public boolean loadKernel()
    {
    	String median_comperror = "";
        try{

            String filename = "/OpenCL/MedianFilter.cl";
            program = clattr.context.createProgram(MedianFilter.class.getResourceAsStream(filename)).build();
        }
        catch(Exception e){
            e.printStackTrace();
            System.out.println("KERNEL Failed to Compile");
            
            StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
            
            median_comperror = errors.toString()+"\n"+
            		"Message exception: "+e.getMessage()+"\n";
            
            monitor.setKeyValue("median.comperror", median_comperror);   
            return false;
        }
        monitor.setKeyValue("median.comperror", median_comperror);   
	    kernel = program.createCLKernel("MedianFilter");
        return true;
    }

    /**!
     * Run the median filter kernel.
     */
	@Override
	public boolean runFilter()
	{
		String median_allocerror = "";
		//out.println("dims: " + atts.width + " " + atts.height + " " + atts.slices + " " + atts.channels);

		long time = nanoTime();

	    int	mid = (atts.height == 1 && atts.slices == 1) ? 1 : 
		    (atts.slices == 1) ? 4 : 
			    13;
		
	    int globalSize[] = {0, 0}, localSize[] = {0, 0};
		clattr.computeWorkingGroupSize(localSize, globalSize, new int[] {atts.width, atts.height, 1});

	    
	    try {
	    	/**!
	    	 * copy data to accelerator.
	    	 */
		    clattr.queue.putWriteBuffer(clattr.inputBuffer, true);

		    /**!
		     * setup parameters.
		     */
		    kernel.setArg(0,clattr.inputBuffer)
		    .setArg(1,clattr.outputBuffer)
		    .setArg(2,atts.width)
		    .setArg(3,atts.height)
		    .setArg(4,atts.maxSliceCount.get(index))
		    .setArg(5,mid);
	        
		    /**!
		     * execute kernel
		     */
	    	clattr.queue.put2DRangeKernel(kernel, 0, 0, 
	    			globalSize[0], globalSize[1],
	    			localSize[0], localSize[1]);
	    } catch (Exception e) {
            StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
            
            median_allocerror = errors.toString()+"\n"+
            		"Message exception: "+e.getMessage()+"\n";
    	    monitor.setKeyValue("median.allocerror", median_allocerror);

            
	    }
	    
	    /**!
	     * Write results.
	     */
	    monitor.setKeyValue("median.allocerror", median_allocerror);
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
    public boolean releaseKernel() {
    	if (!kernel.isReleased()) kernel.release();
    	//program.release();
        return true;
    }

    /**!
     * Create custom user interface.
     */
	@Override
	public Component getFilterWindowComponent() {
		// TODO Auto-generated method stub
		Panel p = new Panel();
		Button b = new Button();
		b.setLabel("No options for: " + getName());
		p.add(b);
		
		return p;
	}

	/**!
	 * Callback to process selection of the filter.
	 */
	@Override
	public void processFilterWindowComponent() {
	}
}
