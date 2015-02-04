package F3DImageProcessing_JOCL_;

import static com.jogamp.opencl.CLMemory.Mem.READ_WRITE;
import ij.ImagePlus;
import ij.process.*;
import ij.ImageStack;

import java.util.*;
import java.util.regex.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.nio.ByteBuffer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLContext;

import ij.WindowManager;

class FilteringAttributes
{
	public static class FilterPanel
	{
		public int L = -1;
		public String maskImage = "";
		ArrayList<ImageStack> maskImages = null;
		
		public String toString() {
			String result = "[{"
							+ "\"maskImage\" : \"" + maskImage + "\""
							+ (maskImage.startsWith("StructuredElementL") ? ", \"maskLen\" : \"" + L + "\"" : "")
							+ "}]";
			return result;
		}
		
		public void fromString(String str) {
			// TALITA JSON parser
			JSONParser parser = new JSONParser();
					
			try {
				 
				Object objFilter = parser.parse(str);
				JSONObject jsonFilterObject = (JSONObject) objFilter;
				
				JSONArray maskArray = (JSONArray) jsonFilterObject.get("Mask");
							
				JSONObject jsonMaskObject = (JSONObject) maskArray.get(0);
				
				maskImage = (String) jsonMaskObject.get("maskImage");
				if (null!=jsonMaskObject.get("maskLen"))
					L = Integer.valueOf((String)jsonMaskObject.get("maskLen"));
				else
					L = -1;
				
		 
			} catch (ParseException e) {
				e.printStackTrace();
			}
						
		}
		
		public JPanel setupInterface() 
	    {
			JPanel panel = new JPanel();	
	        JLabel label = new JLabel("Mask:");
	        
	        //fiji complains when I use JComboBox<String>
	        //@SuppressWarnings({ "rawtypes", "unchecked" }) 
			JComboBox aw = new JComboBox(FilteringAttributes.getImageTitles(true));
	        
	        L = 3;
	        maskImage = (String)aw.getSelectedItem();
	        
	        final JSpinner spinner = new JSpinner(new SpinnerNumberModel(L, 1, 20, 1));
	        
	        aw.setSelectedItem(maskImage);
	        
	        aw.addItemListener(new ItemListener() {
				@Override
				public void itemStateChanged(ItemEvent e) {
					if(e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
						maskImage =  (String) e.getItem();
						spinner.setVisible(maskImage.startsWith("StructuredElementL") ? true : false);
					}
				}
			});

	        spinner.addChangeListener(new ChangeListener() {
				
				@Override
				public void stateChanged(ChangeEvent e) {
					JSpinner s = (JSpinner)e.getSource();
					L = (Integer) s.getValue();
//					System.out.println(L);
				}
			});
	        
	        panel.add(label);
	        panel.add(aw);
	        panel.add(spinner);
	        
	        return panel;
	    }
		
		void processFilterWindowComponent() {
			maskImages = FilteringAttributes.getStructElements(maskImage, L);
			//System.out.println("maskImage: " + maskImage);
			//System.out.println("L: " + L);
		}
	};
	
	public HashMap<String, JOCLFilter> filterList;
	public ArrayList<JOCLFilter> filters;

	public ImagePlus inputImage = null;

	public int width = 0;
    public int height = 0;
    public int channels = 0;
    public int slices = 0;
    
    List<Integer> maxSliceCount = new ArrayList<Integer>();
    public int sliceStart = 0;
    public int sliceEnd = 0;
    List<Integer> overlap = new ArrayList<Integer>();
    
    public boolean intermediateSteps = false;
	public boolean enableZorder = false;
	public boolean useVirtualStack = false;
	public File virtualDirectory = null;
	public boolean chooseConstantDevices = false;
	public int inputDeviceLength = 1;

    /// internalImages
    private static final int MAX_STRUCTELEM_SIZE = 21*21*21;
    private static ArrayList<String> internalImages;

    public int zOrderWidth = 0, zOrderHeight = 0; //zOrderDepth = 0;
    List<Integer> zOrderDepth = new ArrayList<Integer>();

    static {
	     internalImages = new ArrayList<String>();
	
	     internalImages.add("StructuredElementL");        
	     internalImages.add(String.format("Diagonal%dx%dx%d", 3, 3, 3));
	     internalImages.add(String.format("Diagonal%dx%dx%d", 10, 10, 4));
	     internalImages.add(String.format("Diagonal%dx%dx%d", 10, 10, 10));
    }

    public FilteringAttributes() {
    	
    	filterList = new HashMap<String, JOCLFilter>();
    	//filterList = new ArrayList<JOCLFilter>();
        filters = new ArrayList<JOCLFilter>();
        
        /// list all filters..
        ArrayList<JOCLFilter> filters = new ArrayList<JOCLFilter>();
        filters.add(new BilateralFilter());
        filters.add(new MedianFilter());
        filters.add(new MMFilterEro());
        filters.add(new MMFilterDil());
        filters.add(new MMFilterOpe());
        filters.add(new MMFilterClo());
        filters.add(new MaskFilter());
        
        for(int i = 0; i < filters.size(); ++i){
        	filterList.put(filters.get(i).getName(), filters.get(i));
        }
    }
    
    public int roundUp(int groupSize, int globalSize) {
		int r = globalSize % groupSize;
		if (r == 0) {
			return globalSize;
		} else {
			return globalSize + groupSize - r;
		}
	}
    
    public static String[] getImageTitles(boolean includeInternal) 
    {	
		ArrayList<String> activeWindowsList = new ArrayList<String>();
        
        FilteringAttributes.getImageTitles(activeWindowsList, includeInternal);

        String[] activeWindows = new String [activeWindowsList.size()];
        activeWindowsList.toArray(activeWindows);

        return activeWindows;
    }
    
    private static void getImageTitles(ArrayList<String> inputArray, boolean includeInternal) 
    {
        if(includeInternal) 
            inputArray.addAll(internalImages);
       
        int[] idlist = WindowManager.getIDList();
		for(int i = 0; i < idlist.length; ++i)
		{
			ImagePlus imagex = WindowManager.getImage(idlist[i]);
            String title = imagex.getTitle();
			
            if(title.length() == 0)
				title = "Image: " + idlist[i];

			inputArray.add(title);
		}
    }

    private static boolean parseImage(String inputString, ArrayList<ImageStack> images, int maskL)
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
/*
        for(int i = 0; i < images.size(); ++i)
            images.get(i).show();
*/      
        return images;
    }
    
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
    
    private static ArrayList<ImageStack> getStructElements(String maskImage, int maskL)
    {
        ArrayList<ImageStack> images = new ArrayList<ImageStack>();
        parseImage(maskImage, images, maskL);
        return images; 
    }
    
    /// Helper functions..

//    public int[] getStructElementSize(ImageStack stack)
//    {
//        int[] size = new int[3];
//
//        size[0] = stack.getWidth();
//        size[1] = stack.getHeight();
//        size[2] = stack.getSize();
//
//        return size;  
//    }

    public boolean isValidStructElement(ImageStack stack) 
    {
        ///int[] elemSize = getStructElementSize(stack);
        
        if(stack.getWidth()*stack.getHeight()*stack.getSize()>= MAX_STRUCTELEM_SIZE) {
           return false;
        }
        return true;
    }

    public CLBuffer<ByteBuffer> getStructElement(CLContext context, ImageStack stack)
    {
        return getStructElement(context, stack, -1);
    }

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
            //BufferedImage bi = prc.getBufferedImage();
            //DataBufferByte sb = (DataBufferByte) bi.getRaster().getDataBuffer();
            structElem.getBuffer().put((byte[])prc.getPixels());//(sb.getData());
        }

        structElem.getBuffer().position(0);

        return structElem;   
    }
}
