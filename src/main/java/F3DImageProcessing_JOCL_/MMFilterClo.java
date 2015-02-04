package F3DImageProcessing_JOCL_;

import static com.jogamp.opencl.CLMemory.Mem.READ_WRITE;
import static java.lang.System.nanoTime;
import static java.lang.System.out;
import ij.IJ;
import ij.ImageStack;

import java.awt.Component;
import java.nio.ByteBuffer;

import F3DImageProcessing_JOCL_.MMFilterClo;

import com.jogamp.opencl.CLBuffer;

import javax.swing.JPanel;

//Closing
class MMFilterClo implements JOCLFilter
{	
	int index;
	FilteringAttributes.FilterPanel filterPanel =
			new FilteringAttributes.FilterPanel();
	
	@Override
    public JOCLFilter newInstance() {
		return new MMFilterClo();
	}
	
	public String toString() {
		String result = "{ "
				+ "\"Name\" : \"" + getName() + "\" , "
				+ "\"Mask\" : " + filterPanel.toString()  
				+ " }";
			return result;
	}
	
	public void fromString(String str) {
		filterPanel.fromString(str);
	}
	
    @Override
    public String getName()
    {
        return "MMFilterClo";
    }

    @Override
    public int overlapAmount() {
    	int overlapAmount = 0;
    	
    	for(int i = 0; i < filterPanel.maskImages.size(); ++i)
            overlapAmount = Math.max(overlapAmount, 
            						 filterPanel.maskImages.get(i).getSize());
        
        return overlapAmount;
    }

	@Override
	public int getDimensions()
	{
		return 2;
	}

    CLAttributes clattr = null;
    FilteringAttributes atts = null;

    @Override
    public void setAttributes(CLAttributes c, FilteringAttributes a, int idx)
    {
        clattr = c;
        atts = a;
        index = idx;
    }

    MMFilterEro erosion;
    MMFilterDil dilation;

    @Override
    public boolean loadKernel()
    {
        if(clattr.outputTmpBuffer == null)
		    clattr.outputTmpBuffer = clattr.context.createByteBuffer(clattr.inputBuffer.getBuffer().capacity(), READ_WRITE);
        
        erosion = new MMFilterEro();
        dilation = new MMFilterDil();

        erosion.setAttributes(clattr, atts, index);
        erosion.loadKernel();
        
        dilation.setAttributes(clattr, atts, index);      
        dilation.loadKernel();    
        return true;
    }

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

		//out.println("dims: " + atts.width + " " + atts.height + " " + atts.slices + " " + atts.channels);

		long time = nanoTime();

	    clattr.queue.putWriteBuffer(clattr.inputBuffer, false);
        clattr.queue.finish();
		
        dilation.runKernel(filterPanel.maskImages);

		//swap input and output..
        CLBuffer<ByteBuffer> tmpBuffer = clattr.inputBuffer;
        clattr.inputBuffer = clattr.outputBuffer;
        clattr.outputBuffer = tmpBuffer;
        
        erosion.runKernel(filterPanel.maskImages);
        		
        clattr.queue.putReadBuffer(clattr.outputBuffer, false);
        clattr.queue.finish();

        //clattr.outputTmpBuffer.release();

		time = nanoTime() - time;

		//System.out.println("kernel created in :" + ((double)time)/1000000.0);
		
		return true;
	}

    @Override
    public boolean releaseKernel() {
        erosion.releaseKernel();
        dilation.releaseKernel();
//        clattr.outputTmpBuffer.release();
        return true;
    }

	@Override
	public Component getFilterWindowComponent()	{
		JPanel panel = new JPanel();
		panel.add(filterPanel.setupInterface());
		return panel;
	}

	@Override
	public void processFilterWindowComponent() {
		filterPanel.processFilterWindowComponent();
	}
}
