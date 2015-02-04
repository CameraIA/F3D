package F3DImageProcessing_JOCL_;

import static com.jogamp.opencl.CLMemory.Mem.READ_WRITE;
import static java.lang.System.nanoTime;
import static java.lang.System.out;
import ij.IJ;
import ij.ImageStack;

import java.awt.Component;
import java.nio.ByteBuffer;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram;

import java.util.ArrayList;

import javax.swing.JPanel;

//Erosion
class MMFilterEro implements JOCLFilter
{	
	int index;
	FilteringAttributes.FilterPanel filterPanel =
			new FilteringAttributes.FilterPanel();
	
	@Override
    public JOCLFilter newInstance() {
		return new MMFilterEro();
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
        return "MMFilterEro";
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
    CLKernel kernel, kernel2;

    @Override
    public boolean loadKernel()
    {
        try{
            String filename = "/OpenCL/" + (atts.enableZorder ? "MMero3DZorder.cl" : 
                                                                "MMero3D.cl");
            program = clattr.context.createProgram(BilateralFilter.class.getResourceAsStream(filename)).build();    
        } 
        catch(Exception e) {
            e.printStackTrace();
            System.out.println("KERNEL Failed to Compile");
            return false;
        }
        
        if(clattr.outputTmpBuffer == null)
		    clattr.outputTmpBuffer = clattr.context.createByteBuffer(clattr.inputBuffer.getBuffer().capacity(), READ_WRITE);
           
        kernel = program.createCLKernel("MMero3DFilterInit");
        kernel2 = program.createCLKernel("MMero3DFilter");

        return true;
    }

    public void runKernel(ArrayList<ImageStack> maskImages)
    {
        CLBuffer<ByteBuffer> structElem = null;
        
        for(int i = 0; i < maskImages.size(); ++i) 
        {
            ImageStack mask = maskImages.get(i);

            int[] size = new int [3];
            
            size[0] = mask.getWidth();
            size[1] = mask.getHeight();
            size[2] = mask.getSize();
            structElem = atts.getStructElement(clattr.context, mask);
            //out.println("Rank: " + clattr.rank + " Processing: " + i);
            
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

            if(clattr.outputTmpBuffer == null)
            	out.println("clattr.outputTmpBuffer is null!!\n");
            
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

                if(atts.enableZorder) {
                    kernel.setArg(11, clattr.zorder.zIbits)
                          .setArg(12, clattr.zorder.zJbits)
                          .setArg(13, clattr.zorder.zKbits);
                }
		    } else {
              CLBuffer<ByteBuffer> tmpBuffer1, tmpBuffer2;
            
              tmpBuffer1 = i % 2 != 0 ? clattr.outputTmpBuffer : clattr.outputBuffer;
              tmpBuffer2 = i % 2 == 0 ? clattr.outputTmpBuffer : clattr.outputBuffer;
             
              kernel2.setArg(0,clattr.inputBuffer)
		        .setArg(1,tmpBuffer1)
		        .setArg(2,tmpBuffer2)
		        .setArg(3,atts.width)
		        .setArg(4,atts.height)
		        .setArg(5,atts.maxSliceCount.get(index))
		        .setArg(6,structElem)
		        .setArg(7,size[0])
		        .setArg(8,size[1])
		        .setArg(9,size[2])
                .setArg(10,startOffset)
                .setArg(11,endOffset);
              
                if(atts.enableZorder) {
                    kernel2.setArg(12, clattr.zorder.zIbits)
                          .setArg(13, clattr.zorder.zJbits)
                          .setArg(14, clattr.zorder.zKbits);
                }  
            }
            clattr.queue.putWriteBuffer(structElem, false);
		    clattr.queue.put2DRangeKernel(i == 0 ? kernel : kernel2, 0, 0, 
                                                clattr.globalSize[0], clattr.globalSize[1], 
                                                clattr.localSize[0], clattr.localSize[1]);

            //clattr.queue.finish();
            structElem.release();
    	}

        if(maskImages.size() % 2 != 0) {
            CLBuffer<ByteBuffer> tmpBuffer = clattr.outputTmpBuffer;
            clattr.outputTmpBuffer = clattr.outputBuffer;
            clattr.outputBuffer = tmpBuffer;
        }
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

		//out.println("ero dims: " + atts.width + " " + atts.height + " " + atts.slices + " " + atts.channels);

		long time = nanoTime();

        clattr.queue.putWriteBuffer(clattr.inputBuffer, false);
        clattr.queue.finish();

        runKernel(filterPanel.maskImages);
		
        clattr.queue.putReadBuffer(clattr.outputBuffer, false);
        clattr.queue.finish();
        
        //kernel.release();
        //kernel2.release();

        time = nanoTime() - time;
        //System.out.println("kernel created in :" + ((double)time)/1000000.0);

		return true;
	}

    @Override
    public boolean releaseKernel()
    {
        //clattr.outputTmpBuffer.release();
    	if (!kernel.isReleased()) kernel.release();
        if (!kernel2.isReleased()) kernel2.release();
        return true;
    }

	@Override
	public Component getFilterWindowComponent() {
		JPanel panel = new JPanel();
        panel.add(filterPanel.setupInterface());
        return panel;
	}

	@Override
	public void processFilterWindowComponent() {
		filterPanel.processFilterWindowComponent();
	}
}
