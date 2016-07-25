package F3DImageProcessing_JOCL_;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLDevice;

import static com.jogamp.opencl.CLMemory.Mem.READ_WRITE;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**!
 * Class to hold OpenCL output and buffers associated with a particular device and queue.
 * @author hari
 * @author tperciano
 *
 */
class CLAttributes {
	public CLContext context = null; /// OpenCL context
	public CLDevice device = null;  /// OpenCL device
	public CLCommandQueue queue = null; ///Queue for OpenCL device.

	///TODO: Replace this with a Buffer manager.
	public CLBuffer<ByteBuffer> inputBuffer = null;
    public CLBuffer<ByteBuffer> outputBuffer = null;
    public CLBuffer<ByteBuffer> outputTmpBuffer = null;
   
    /**!
     * Helper function to round to nearest buffer that aligns with groupSize.
     * @param groupSize - The size of each group
     * @param globalSize - The current size of the buffer.
     * @return new size
     */
    public int roundUp(int groupSize, int globalSize) {
		int r = globalSize % groupSize;
		if (r == 0) {
			return globalSize;
		} else {
			return globalSize + groupSize - r;
		}
	}
    
    /**
     * Calculates working group sizes
     * @param  localSize   Working group size (local)
     * @param  globalSize  Working group size (global)
     * @param  sizes       Dimensions
     * @return             Returns false if an error occurred
     */
    boolean computeWorkingGroupSize(int[] localSize, int[] globalSize, int[] sizes)
    {
    	if(localSize == null || globalSize == null || sizes == null) {
    		return false;
    	}

    	/**!
    	 * Allocate 1D or 2D working groups. Set sizes based on 3D array.
    	 */
    	if(localSize.length <= 0 || localSize.length > 2 ||
    	   globalSize.length <= 0 || globalSize.length > 2 ||
    	   sizes.length != 3) {
    		return false;
    	}
    	
    	String deviceType = device.getType().toString();
    	
    	/**!
    	 * Set working group sizes
    	 */
    	int dimensions = globalSize.length;
    	//atts.width*atts.height*(maxSliceCount + overlapAmount)
    	if(dimensions == 1)
    	{
    		localSize[0] = device.getMaxWorkGroupSize();
    		globalSize[0] = roundUp(localSize[0], sizes[0]*sizes[1]*sizes[2]);
    	}
    	else if(dimensions == 2)
    	{
    		localSize[0] = Math.min((int)Math.sqrt(device.getMaxWorkGroupSize()), 16);
    		globalSize[0] = roundUp(localSize[0], sizes[0]);

    		localSize[1] = Math.min((int)Math.sqrt(device.getMaxWorkGroupSize()), 16);
    		globalSize[1] = roundUp(localSize[1], sizes[1]);
    	}
    	
    	/// only use on process if you are CPU?
    	/// TODO: test this case (if CPU still fails)
    	if(deviceType.equals("CPU")) {
    		for(int i = 0; i < dimensions; ++i) {
    			globalSize[i] *= localSize[i];
    			localSize[i] = 1;
    		}
    	}

    	return true;
    }

    /**
     * Initializes data for execution
     * @param  image         Input image      
     * @param  atts          Filtering attributed
     * @param  overlapAmount Size of overlap
     * @param  maxSliceCount Number of image slices to be executed
     * @return               Returns false if an error occurred
     */
    boolean initializeData(ImagePlus image, FilteringAttributes atts, 
    						int overlapAmount, int maxSliceCount) 
    {
        String deviceType = device.getType().toString();
        int globalMemSize = (int) Math.min(device.getMaxMemAllocSize()*.4, Integer.MAX_VALUE >> 1);

        int[] dims = image.getDimensions();

        atts.width = dims[0];
        atts.height = dims[1];
        atts.channels = dims[2];
        atts.slices = dims[3];

        atts.sliceStart = -1;
        atts.sliceEnd = -1;
        
        if(deviceType.equals("CPU")) {
            globalMemSize = (int) Math.min(globalMemSize, 10*1024*1024); /// process 100MB at a time
        }


        //atts.maxSliceCount -= overlapAmount; 
        //atts.overlap = overlapAmount;
        
        ///Return if the device cannot hold any slices.
        if(maxSliceCount <= 0){
            System.out.println("Image + StructureElement will not fit on GPU memory");
            return false;
        }            

        /**!
         * Compute total size based on Z direction 
         * given width, height and maximumSlice and amount of overlap
         */
        int totalSize = atts.width*atts.height*(maxSliceCount + (overlapAmount*2));
 
        /**!
         * create OpenCL buffers.
         */
        inputBuffer = context.createByteBuffer(totalSize, READ_WRITE); 
        outputBuffer = context.createByteBuffer(totalSize, READ_WRITE); 
        
        return true;
    }
    
    
    /**!
     * Loads data between startRange and endRange slices from stack into device. 
     * The overlap includes pad that allows the next filter to execute properly. 
     * @param stack      Image stack
     * @param atts       Filtering attributes
     * @param startRange Slice index to start
     * @param endRange   Slice index to end
     * @param overlap    Overlap size
     * @return           Returns false if an error occurred
     */
    boolean loadNextData(ImageStack stack, FilteringAttributes atts, int startRange, int endRange, int overlap)
    {

    	/**!
    	 * minindex starts at the minimum slice index needed to successfully run the kernel.
    	 * maxindex ends with the maximum slice index needed to successfully run the kernel
    	 */
        int minIndex = Math.max(0, startRange-overlap);
        int maxIndex = Math.min(atts.slices, endRange+overlap);
       
        /// load data..
        
        ByteBuffer buffer = (ByteBuffer)inputBuffer.getBuffer();

        buffer.position(0);
                
        for(int i = minIndex;  i < maxIndex; ++i)
        {
            //ByteProcessor prc  = (ByteProcessor) stack.getProcessor(i+1);
        	///The following code implicitly converts between any type to BYTE type
        	///TODO: Fix so that any type of kernels handled..
			ByteProcessor prc  = (ByteProcessor) stack.getProcessor(i+1).convertToByteProcessor();
            buffer.put((byte[])prc.getPixels());            
        }

        ///rewind buffer position back to zero.
        buffer.position(0);

        return true;
    }

    /**
     * Writes data between startRange and endRange slices fron device into stack
     * @param imagestack - Output imagestack
     * @param atts - attributes of the pipeline
     * @param startRange - start range within index stack
     * @param endRange - end range within index stack.
     * @param overlap - amount of overlap that is present
     */
    void writeNextData(ImageStack imagestack, FilteringAttributes atts, int startRange, int endRange, int overlap) 
    {
    	/**!
    	 * Since the incoming index is padded with overlap,
    	 * remove the padding to get only the slices of interest.
    	 * minIndex should start past overlap, unless it is at 0
    	 * maxIndex
    	 */        
        int startIndex = startRange == 0 ? 0 : overlap;
        int length = endRange-startRange;
        
        ByteBuffer buffer = (ByteBuffer) outputBuffer.getBuffer();

        buffer.position(startIndex*atts.width*atts.height);

        for(int i = 0; i < length; ++i)
        {
            ByteProcessor prc = null;

            byte[] output = new byte [atts.width*atts.height];

            buffer.get(output, 0, atts.width*atts.height);
            
            prc = new ByteProcessor(atts.width, atts.height, output);
            imagestack.addSlice((ImageProcessor)prc);   		
            
        }
        buffer.position(0);        
    }

    /**!
     * Swap the contents of the buffer.
     */
    void swapBuffers() {
        CLBuffer<ByteBuffer> tmpBuffer = inputBuffer;
        inputBuffer = outputBuffer;
        outputBuffer = tmpBuffer;
    }
    
    /**
     * Converts byte buffer to float buffer
     * @param buffer Buffer to be converted
     * @return       Converted buffer
     */
    public CLBuffer<FloatBuffer> convertToFloatBuffer(CLBuffer<ByteBuffer> buffer) {
    	CLBuffer<FloatBuffer> fbuffer = context.createFloatBuffer(buffer.getCLCapacity(), READ_WRITE); 		
    	
    	for(int i = 0; i < buffer.getCLCapacity(); ++i) {
    		fbuffer.getBuffer().put(i, (float)buffer.getBuffer().get(i));
    	}
    	
    	return fbuffer;
    }
    /**
     * Converts float buffer to byte buffer
     * @param fbuffer Buffer to be converted
     * @return        Converted buffer
     */
    public CLBuffer<ByteBuffer> convertToByteBuffer(CLBuffer<FloatBuffer> fbuffer) {
    	
    	CLBuffer<ByteBuffer> buffer = context.createByteBuffer(fbuffer.getCLCapacity(), READ_WRITE);
    	
    	for(int i = 0; i < fbuffer.getCLCapacity(); ++i) {
    		buffer.getBuffer().put(i, (byte)fbuffer.getBuffer().get(i));
    	}
    	
    	return buffer;
    }
}
