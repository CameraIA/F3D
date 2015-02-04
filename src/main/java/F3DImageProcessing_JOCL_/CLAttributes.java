package F3DImageProcessing_JOCL_;

import java.nio.ByteBuffer;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLDevice;

import static com.jogamp.opencl.CLMemory.Mem.READ_WRITE;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

class CLAttributes {
    public int rank = 0;
    public int minWorkGroup = 256;
    public int[] globalSize = null;
    public int[] localSize = null;
	//public int totalSize = 0;
	public CLContext context = null;
	public CLDevice device = null;
	public CLCommandQueue queue = null;
	public CLBuffer<ByteBuffer> inputBuffer = null;
    public CLBuffer<ByteBuffer> outputBuffer = null;
    public CLBuffer<ByteBuffer> outputTmpBuffer = null;

    public Zorder<Long> zorder = null; //new Zorder<Long>();

    boolean initializeData(int dimensions, ImagePlus image, FilteringAttributes atts, int overlapAmount, int maxSliceCount) 
    {

        String deviceType = device.getType().toString();
        int globalMemSize = (int) Math.min(device.getMaxMemAllocSize()*.8, Integer.MAX_VALUE >> 1);
		//int globalMemSize = (int) Math.min(device.getMaxMemAllocSize()*.1, Integer.MAX_VALUE >> 1); Hari version

        
        int[] dims = image.getDimensions();

        atts.width = dims[0];
        atts.height = dims[1];
        atts.channels = dims[2];
        atts.slices = dims[3];

        if(deviceType.equals("CPU")) {
            globalMemSize = (int) Math.min(globalMemSize, 10*1024*1024); /// process 100MB at a time
            minWorkGroup = 64;
        }

        if(atts.enableZorder) {
            zorder = new Zorder<Long>();
        }

        //System.out.println("-->" + globalMemSize + " " + atts.width + " " + atts.height + " " + atts.slices);

        if(!atts.enableZorder) {
            //atts.maxSliceCount = (int)(((double)globalMemSize / ((double)atts.width*atts.height)));
        }
        else 
        {
            ///now allocate memory for Zorder memory on GPU..
//            System.out.println("-->" + atts.width + " " + atts.height + " " + atts.slices);

            atts.zOrderWidth = zorder.zoRoundUpPowerOfTwo(atts.width);
            atts.zOrderHeight = zorder.zoRoundUpPowerOfTwo(atts.height);
            
            //atts.maxSliceCount = (int)(((double)globalMemSize / ((double)atts.zOrderWidth*atts.zOrderHeight)));
            //atts.zOrderDepth = zorder.zoRoundUpPowerOfTwo(atts.maxSliceCount);                                
        } 

        //atts.maxSliceCount -= overlapAmount; 
        //atts.overlap = overlapAmount;
        
        if(maxSliceCount <= 0){
            System.out.println("Image + StructureElement will not fit on GPU memory");
            return false;
        }            
    
        /// if the maximum slices are greater than available slices then
        /// clamp to slices
//        if(atts.maxSliceCount > atts.slices) {
//            System.out.println("Max slice count : " + atts.maxSliceCount + " " + atts.slices + " " + atts.overlap);
//            atts.maxSliceCount = atts.slices;
//            //atts.maxSliceCount = -1;
//            atts.overlap = 0;
//        }

        if(atts.enableZorder) 
        {
            //atts.zOrderDepth = zorder.zoRoundUpPowerOfTwo(maxSliceCount + overlapAmount);
            //System.out.println("-->" + atts.zOrderWidth + " " + atts.zOrderHeight + " " + atts.zOrderDepth);
            ///now allocate memory for Zorder memory on GPU..
            zorder.zoInitZOrderIndexing(atts.width, atts.height, maxSliceCount + overlapAmount, context);
        }            
//        System.out.println("Overlap: " + maxSliceCount + " " + overlapAmount);

        /// Initialize buffers..
    
        int totalSize = 0;

        globalSize = new int [dimensions];
        localSize = new int [dimensions];

        if(dimensions == 2)
        {
            localSize[0] = Math.min((int)Math.sqrt(device.getMaxWorkGroupSize()), 16);
            globalSize[0] = atts.roundUp(localSize[0], atts.width);

            localSize[1] = Math.min((int)Math.sqrt(device.getMaxWorkGroupSize()), 16);
            globalSize[1] = atts.roundUp(localSize[1], atts.height);
        }
        else
        {
            localSize[0] = device.getMaxWorkGroupSize();
            globalSize[0] = atts.roundUp(localSize[0], atts.width*atts.height*(maxSliceCount + overlapAmount));
        }

        /// only use on process if you are CPU?
        if(deviceType.equals("CPU")) {
            for(int i = 0; i < dimensions; ++i) {
               globalSize[i] *= localSize[i];
               localSize[i] = 1;
            }
        }

        if(atts.enableZorder){
            totalSize = zorder.zoGetIndexZOrder(atts.width-1, atts.height-1, (maxSliceCount + overlapAmount)-1)+1;
        }        
        else {
            totalSize = atts.width*atts.height*(maxSliceCount + overlapAmount);
        }

//        System.out.println(atts.width + " " + atts.height + " " + maxSliceCount + " " + overlapAmount + " totalSize: " + totalSize);
        
        inputBuffer = context.createByteBuffer(totalSize, READ_WRITE); 
        outputBuffer = context.createByteBuffer(totalSize, READ_WRITE); 

        //out.println("device bytes: " + (int)((double)globalMemSize/(1024.0*1000.0)) 
        //+ " " + atts.maxSliceCount  + " " + 
        //(int)((atts.maxSliceCount*atts.width*atts.height)/(1024.0*1000.0)));
        atts.sliceStart = -1;
        atts.sliceEnd = -1;

        return true;
    }
    
    boolean loadNextData(ImageStack stack, FilteringAttributes atts, int startRange, int endRange, int overlap)
    {

        int minIndex = Math.max(0, startRange-overlap);
        int maxIndex = Math.min(atts.slices, endRange+overlap);
        
//        System.out.println("Load minIndex: " + minIndex + " " + "maxIndex: " + maxIndex);
        
        /*if(atts.sliceEnd >= atts.slices)
            return false;

        if(atts.sliceStart == -1) {
            atts.sliceStart = 0;
            atts.sliceEnd = atts.maxSliceCount;
        }
        else {
            atts.sliceStart = atts.sliceEnd;
            atts.sliceEnd += atts.maxSliceCount;
        }

        if(atts.sliceEnd > atts.slices) {
            atts.maxSliceCount -= (atts.sliceEnd - atts.slices);
            atts.sliceEnd = atts.slices;
        }
        
        int overlap = (int) (atts.overlap/2);

        int minIndex = Math.max(0, atts.sliceStart - overlap);
        int maxIndex = Math.min(atts.sliceEnd + overlap, atts.slices);

        System.out.println("start: " + minIndex + 
                           " end: " + maxIndex + 
                           " for slices at start: " + atts.sliceStart + 
                           " end: " + atts.sliceEnd + 
                           " maxSlices: " + atts.maxSliceCount + 
                           " total: " + atts.slices + " " + atts.overlap);*/

        
        /// load data..
        ByteBuffer buffer = (ByteBuffer)inputBuffer.getBuffer();

        buffer.position(0);
        
        for(int i = minIndex;  i < maxIndex; ++i)
        {
            //ByteProcessor prc  = (ByteProcessor) stack.getProcessor(i+1);
			ByteProcessor prc  = (ByteProcessor) stack.getProcessor(i+1).convertToByteProcessor(); //Hari version
            
            if(!atts.enableZorder) 
                buffer.put((byte[])prc.getPixels());            
            else {
            	byte[] pixels = (byte[])prc.getPixels();
                int l = 0;        
                for(int j = 0; j < atts.height; ++j)
                {
                    for(int k = 0; k < atts.width; ++k)
                    {    
                        if(buffer.capacity() <= zorder.zoGetIndexZOrder(k,j,i)) 
                            System.out.println("Greater: " + k + " " + j + " " + i + " " + zorder.zoGetIndexZOrder(k,j,i));
                        buffer.put(zorder.zoGetIndexZOrder(k,j,i),pixels[l]);
                        l++;
                    }
                }
            }
        }

        buffer.position(0);

        //System.out.println("Loaded! minIndex: " + minIndex + " " + "maxIndex: " + maxIndex);
        
        return true;
    }

    void writeNextData(ImageStack imagestack, FilteringAttributes atts, int startRange, int endRange, int overlap) 
    {
        int minIndex = startRange == 0 ? 0 : overlap;
        int maxIndex = (endRange-startRange);
        
//        System.out.println("Write minIndex: " + minIndex + " " + "maxIndex: " + maxIndex);

        if(minIndex != 0)
            maxIndex += 1;

        ByteBuffer buffer = (ByteBuffer) outputBuffer.getBuffer();

        if(atts.enableZorder)
            buffer.position(minIndex*atts.zOrderWidth*atts.zOrderHeight);
        else
            buffer.position(minIndex*atts.width*atts.height);

        for(int i = minIndex; i < maxIndex; ++i)
        {
            ByteProcessor prc = null;

            byte[] output = new byte [atts.width*atts.height];

            if(!atts.enableZorder) { 
                buffer.get(output, 0, atts.width*atts.height);
            }
            else {
                int l = 0;
                for(int j = 0; j < atts.height; ++j)
                {
                    for(int k = 0; k < atts.width; ++k) {
                        output[l] = buffer.get(zorder.zoGetIndexZOrder(k,j,i));
                        l++;
                    }
                }
            }

            prc = new ByteProcessor(atts.width, atts.height, output);
            imagestack.addSlice((ImageProcessor)prc);   		
            
        }

//        ImagePlus outputImagetmp = new ImagePlus("Temp", imagestack);
//		outputImagetmp.show();
        
		//System.out.println("Wrote! minIndex: " + minIndex + " " + "maxIndex: " + maxIndex);
		
        buffer.position(0);
        
    }

    void swapBuffers() {
        CLBuffer<ByteBuffer> tmpBuffer = inputBuffer;
        inputBuffer = outputBuffer;
        outputBuffer = tmpBuffer;
    }
}
