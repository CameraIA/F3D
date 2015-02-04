package F3DImageProcessing_JOCL_;

import static java.lang.System.nanoTime;
import ij.IJ;

import java.awt.Button;
import java.awt.Component;
import java.awt.Panel;

import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram;

/*
 * MedianFilter implements the 3D median filter without sorting optimization - TODO: use quicksort
 */

class MedianFilter implements JOCLFilter
{
	int index;
    @Override
    public JOCLFilter newInstance() {
		return new MedianFilter();
	}
	
    public String toString() {
    	String result = "{ "
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\" }";
		return result;
	}
	
	public void fromString(String options) {
		///parse options
	}
	
    @Override
    public String getName()
    {
        return "MedianFilter";
    }

    @Override
    public int overlapAmount() {
        return 0;
    }

	@Override
	public int getDimensions()
	{
		return 2;
	}

    CLAttributes clattr;
    FilteringAttributes atts;

    @Override
    public void setAttributes(CLAttributes c, FilteringAttributes a, int idx)
    {
        clattr = c;
        atts = a;
        index = idx;
    }

    CLProgram program;
    CLKernel kernel;

    @Override
    public boolean loadKernel()
    {
        try{

            String filename = "/OpenCL/" + (atts.enableZorder ? "MedianFilterZorder.cl" : 
                                                    "MedianFilter.cl");
            program = clattr.context.createProgram(MedianFilter.class.getResourceAsStream(filename)).build();
        }
        catch(Exception e){
            e.printStackTrace();
            System.out.println("KERNEL Failed to Compile");
            return false;
        }
        
	    kernel = program.createCLKernel("MedianFilter");
        return true;
    }

	@Override
	public boolean runFilter()
	{
		//out.println("dims: " + atts.width + " " + atts.height + " " + atts.slices + " " + atts.channels);

		long time = nanoTime();

	    int	mid = (atts.height == 1 && atts.slices == 1) ? 1 : 
		    (atts.slices == 1) ? 4 : 
			    13;

	    kernel.setArg(0,clattr.inputBuffer)
	    .setArg(1,clattr.outputBuffer)
	    .setArg(2,atts.width)
	    .setArg(3,atts.height)
	    .setArg(4,atts.maxSliceCount.get(index))
	    .setArg(5,mid);


        if(atts.enableZorder) {
            kernel.setArg(6, clattr.zorder.zIbits)
                  .setArg(7, clattr.zorder.zJbits)
                  .setArg(8, clattr.zorder.zKbits);
        }
        
        
	    //write out results..
	    //for some reason jogamp on fiji does not have 3DRangeKernel call..
	    clattr.queue.putWriteBuffer(clattr.inputBuffer, true);
	    clattr.queue.finish();
	    
	    clattr.queue.putWriteBuffer(clattr.outputBuffer, true);
        clattr.queue.put2DRangeKernel(kernel, 0, 0, 
                                              clattr.globalSize[0], clattr.globalSize[1],
                                              clattr.localSize[0], clattr.localSize[1]);
	    clattr.queue.putReadBuffer(clattr.outputBuffer, true);
	    
	    clattr.queue.finish();

	    time = nanoTime() - time;

	    //System.out.println("kernel created in :" + ((double)time)/1000000.0);
	    
		return true;
	}

    @Override
    public boolean releaseKernel() {
    	if (!kernel.isReleased()) kernel.release();
    	//program.release();
        return true;
    }

	@Override
	public Component getFilterWindowComponent() {
		// TODO Auto-generated method stub
		Panel p = new Panel();
		Button b = new Button();
		b.setLabel("No options for: " + getName());
		p.add(b);
		
		return p;
	}

	@Override
	public void processFilterWindowComponent() {
		// TODO Auto-generated method stub
		
	}
}
