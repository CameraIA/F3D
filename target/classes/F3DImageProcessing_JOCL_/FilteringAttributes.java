package F3DImageProcessing_JOCL_;

import static com.jogamp.opencl.CLMemory.Mem.READ_WRITE;
import ij.ImagePlus;
import ij.process.*;
import ij.ImageStack;

import java.util.*;
import java.util.regex.*;
import java.nio.ByteBuffer;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLContext;

import ij.WindowManager;

/**!
 * General class to represent attributes needed for filtering
 * such as filter pipeline, image width, height, number of slices to process.
 * Also contains helper functions to generate masks.
 * @author hari
 *
 */
class FilteringAttributes
{
	ArrayList<JOCLFilter> pipeline = new ArrayList<JOCLFilter>();
	
	public int width = 0;
    public int height = 0;
    public int channels = 0;
    public int slices = 0;
    
    public int sliceStart = 0, sliceEnd = 0;
    public int maxOverlap = 0;
    
    List<Integer> overlap = new ArrayList<Integer>();
    List<Integer> maxSliceCount = new ArrayList<Integer>();
    
    public boolean intermediateSteps = false;
    public boolean preview = false;
	public boolean chooseConstantDevices = false;
	public int inputDeviceLength = 1;
    
    /**
     * Get list of titles of opened images
     * @param  includeInternal Include all internal images or not
     * @return                 List of string with image titles
     */
    public static String[] getImageTitles(boolean includeInternal) 
    {	
		ArrayList<String> activeWindowsList = new ArrayList<String>();
        
        FilteringAttributes.getImageTitles(activeWindowsList, includeInternal);

        String[] activeWindows = new String [activeWindowsList.size()];
        activeWindowsList.toArray(activeWindows);

        return activeWindows;
    }
    
    private static final int MAX_STRUCTELEM_SIZE = 21*21*21;
    private static ArrayList<String> internalImages;

    /**!
     * Static construction of masks.
     */
    static {
	     internalImages = new ArrayList<String>();
	
	     internalImages.add("StructuredElementL");        
	     internalImages.add(String.format("Diagonal%dx%dx%d", 3, 3, 3));
	     internalImages.add(String.format("Diagonal%dx%dx%d", 10, 10, 4));
	     internalImages.add(String.format("Diagonal%dx%dx%d", 10, 10, 10));
   }
	
    /**!
     * Create list of available images from Fiji 
     * @param inputArray
     * @param includeInternal (include ones we create internally)
     */
    private static void getImageTitles(ArrayList<String> imageArray, boolean includeInternal) 
    {
        if(includeInternal) 
        	imageArray.addAll(internalImages);
       
        int[] idlist = WindowManager.getIDList();
		for(int i = 0; i < idlist.length; ++i)
		{
			ImagePlus imagex = WindowManager.getImage(idlist[i]);
            String title = imagex.getTitle();
			
            if(title.length() == 0)
				title = "Image: " + idlist[i];

            imageArray.add(title);
		}
    }

    /**!
     * Parse selection and mask return imageStack
     * @param inputString - input name of desired image
     * @param images - output stack
     * @param maskL - optionally mask for desired image
     * @return whether operation was successful
     */
    private static boolean parseImage(String inputString, int maskL, ArrayList<ImageStack> images)
    {
        if(inputString.length() == 0)
            return false;

        if(inputString.startsWith("StructuredElementL"))
        {
            if(images != null)
                images.addAll(buildStructElementArray(maskL));
            return true;
        }

        else if(inputString.startsWith("Diagonal"))
        {
            Pattern p = Pattern.compile("Diagonal(\\d+)x(\\d+)x(\\d+)");
            Matcher m = p.matcher(inputString);
     
            m.matches();

            int x = Integer.parseInt(m.group(1));
            int y = Integer.parseInt(m.group(2));
            int z = Integer.parseInt(m.group(3));

            if(images != null)
                images.add(buildDiagonalImage(x,y,z));
                
            return true;
        }
        else
        {
            int[] idlist = WindowManager.getIDList();
            for(int i = 0; i < idlist.length; ++i)
    		{
    			ImagePlus imagex = WindowManager.getImage(idlist[i]);
                String title = imagex.getTitle();
                if(title.equals(inputString) || title.equals("Image: " + idlist[i])) {
                    if(images != null){
                        images.add(imagex.getStack());
                    }
                    return true;
                }
            }
        }

        return false;
    }
 
    /**!
     * Build an ImageStack from Diagonal lengths.
     * @param L - diagonal length.
     * @return return number of stacks to fulfill request.
     */
    private static ArrayList<ImageStack> buildStructElementArray(int L)
    {
        ArrayList<ImageStack> images = new ArrayList<ImageStack>();
        ByteProcessor processor = null;
        ImageStack stack = null;        
        //Create linear structuring element in 3d

        //L = 100;//variable length ;use only odd numbers for width
        int S=1;
        int middle = ((int)Math.floor(L/2)) + 1;
        //print("Length="+L + "middle="+middle);

        // type 1,2,3
        stack = ImageStack.create(S, L, S, 8);
        images.add(stack);

        stack = ImageStack.create(L, S, S, 8);
        images.add(stack);

        stack = ImageStack.create( S, S, L, 8);
        images.add(stack);
        
        //diagonals
        stack = ImageStack.create(L, L, L, 8);
        processor = (ByteProcessor)stack.getProcessor(middle);
        for (int j=0;j<L;j++)
            processor.set(j,j,255);
        images.add(stack);

        stack = ImageStack.create(L, L, L, 8);
        processor = (ByteProcessor)stack.getProcessor(middle);
        for (int j=0;j<L;j++)
            processor.set(j,L-j-1,255);
        images.add(stack);

        stack = ImageStack.create( L, L, L, 8);
        for (int j=0;j<L;j++){
            processor = (ByteProcessor)stack.getProcessor(j+1);
            processor.set(j,j,255);
        }		
        images.add(stack);

        //Top-left into Bottom-right
        stack = ImageStack.create(L, L, L, 8);
        for (int j=0;j<L;j++){
            processor = (ByteProcessor)stack.getProcessor(j+1);
            processor.set(j,L-j-1,255);
        }
        images.add(stack);
				
        //Bottom-left into Top-right
        stack = ImageStack.create(L, L, L, 8);
        for (int j=0;j<L;j++){
            processor = (ByteProcessor)stack.getProcessor(j+1);
            processor.set(j,L-j-1,255);
        }				
        images.add(stack);
        
        //Bottom-right into Top-left
        stack = ImageStack.create(L, L, L, 8);
        for (int j=0;j<L;j++){
            processor = (ByteProcessor)stack.getProcessor(j+1);
            processor.set(L-j-1,L-j-1,255);
        }
        images.add(stack);

        //Top-left into bottom-
        stack = ImageStack.create(L, L, L, 8);
        for (int j=0;j<L;j++){
            processor = (ByteProcessor)stack.getProcessor(j+1);
            processor.set(L-j-1,j,255);
        }
        images.add(stack);
        //for(int i = 0; i < images.size(); ++i)
        //    images.get(i).show();
        return images;
    }
    
    /**!
     * Build diagonal image given width, height, and number of slices.
     * @param width - width of image
     * @param height - height of image
     * @param slices - depth of image
     * @return image stack 
     */
    private static ImageStack buildDiagonalImage(int width, int height, int slices) 
    {
        ImageStack stack = ImageStack.create(width, height, slices, 8);

        for(int i = 0; i < stack.getSize(); ++i)
        {
            ByteProcessor prc  = (ByteProcessor)stack.getProcessor(i+1);
            int endIndex = prc.getWidth() < prc.getHeight() ? prc.getWidth() : prc.getHeight();
            for(int j = 0; j < endIndex; ++j)
                    prc.set(j,j, 255);
        }

        return stack;
    }
    
    /**!
     * Helper function signature to return stack of images given mask name and diagonal length.
     * @param maskImage - mask name
     * @param maskL - length
     * @return array of image stacks 
     */
    public static ArrayList<ImageStack> getStructElements(String maskImage, int maskL)
    {
        ArrayList<ImageStack> images = new ArrayList<ImageStack>();
        parseImage(maskImage, maskL, images);
        return images; 
    }
    

    /**!
     * Test if stack is valid
     * @param stack
     * @return whether stack contains of valid structure (TODO: create better test)
     */
    public boolean isValidStructElement(ImageStack stack) 
    {        
        if(stack.getWidth()*stack.getHeight()*stack.getSize()>= MAX_STRUCTELEM_SIZE) {
           return false;
        }
        return true;
    }

    /**!
     * Return CL data from image stack
     * @param context - OpenCL context
     * @param stack - input image stack
     * @return - returns CL buffer.
     */
    public CLBuffer<ByteBuffer> getStructElement(CLContext context, ImageStack stack)
    {
        return getStructElement(context, stack, -1);
    }

    /**!
     * Create buffer but with the size overridden
     * @param context - OpenCL context
     * @param stack - input image stack
     * @param overrideSize - new size
     * @return output buffer
     */
    public CLBuffer<ByteBuffer> getStructElement(CLContext context, ImageStack stack, int overrideSize) 
    {
        //if(!isValidImage(stack.getTitle()))
        //    return null;

        CLBuffer<ByteBuffer> structElem;

        int[] size = new int [3];
        size[0] = stack.getWidth();
        size[1] = stack.getHeight();
        size[2] = stack.getSize();
        
        if(overrideSize >= size[0]*size[1]*size[2])
		    structElem = context.createByteBuffer(overrideSize, READ_WRITE);
        else
            structElem = context.createByteBuffer(size[0]*size[1]*size[2], READ_WRITE);

        structElem.getBuffer().position(0);

        for(int i = 0; i < size[2]; ++i)
        {
            ByteProcessor prc  = (ByteProcessor)stack.getProcessor(i+1);
            structElem.getBuffer().put((byte[])prc.getPixels());//(sb.getData());
        }

        structElem.getBuffer().position(0);

        return structElem;   
    }
}
