package F3DImageProcessing_JOCL_;

import static java.lang.System.nanoTime;
import static java.lang.System.out;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.IJ;
import ij.VirtualStack;
import ij.io.FileSaver;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.ArrayList;

import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLContext;
import java.util.Collections;

import javax.swing.JOptionPane;

public class F3DImageProcessing_JOCL_ implements PlugInFilter
{

	//Debugging..
    public static void main(String[] args) {
        // set the plugins.dir property to make the plugin appear in the Plugins menu
    	   	
        Class<?> clazz = F3DImageProcessing_JOCL_.class;
        //System.out.println(clazz.getName());

        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
 
        //System.out.println(url + " " + pluginsDir);
        
        System.setProperty("plugins.dir", pluginsDir);
        
        // start ImageJ
        ImageJ j = new ImageJ();
		j.addWindowListener(new WindowListener() {
			@Override public void windowOpened(WindowEvent e) {}
			@Override public void windowIconified(WindowEvent e) {}
			@Override public void windowDeiconified(WindowEvent e) {}
			@Override public void windowDeactivated(WindowEvent e) {}
			@Override public void windowClosing(WindowEvent e) {}
			@Override public void windowActivated(WindowEvent e) {}
			@Override public void windowClosed(WindowEvent e) {
				System.exit(0);
			}
		});
		
        ImagePlus image1 = IJ.openImage("http://imagej.nih.gov/ij/images/bat-cochlea-renderings.zip");
		//ImagePlus image2 = IJ.openImage("/home/users/tperciano/Desktop/SubstackTmp.tif");
        image1.show();
        //image2.show();
        
        // run the plugin
        IJ.runPlugIn(clazz.getName(), "");
    }

    boolean isVirtual = false;
    File virtualDirectory = null;
    VirtualStack virtualStack = null;
    
    ImagePlus inputImage;
    int startIndex = 0;

    public class StackRange implements Comparable<StackRange>
    {
        long time;
        int startRange;
        int endRange;
        String type;
        ImageStack stack;

        public int compareTo(StackRange sr) {
        	/// ascending
            return startRange - sr.startRange;
        }
    };

    ArrayList<StackRange> stacks = new ArrayList<StackRange>();

    public synchronized boolean getNextRange(int[] range, int sliceCount) {
        int endIndex = inputImage.getDimensions()[3];

        if(startIndex >= endIndex)
            return false;
	
        range[0] = startIndex;
        range[1] = startIndex + sliceCount;
        if(range[1] >= endIndex)
            range[1] = endIndex;
	
        ///update location of range
        startIndex = range[1];
        return true;
    }
    
    public synchronized void addResultStack(int startRange, int endRange, ImageStack stack, String type, long time) {
    	int slices = inputImage.getDimensions()[3];
	
	    StackRange sr = new StackRange();
	    sr.startRange = startRange;
	    sr.endRange = endRange;
	    sr.stack = stack;
	    sr.type = type;
	    sr.time = time;
	
	    if(!isVirtual) {
	    	stacks.add(sr);
	    } else {       	
	      	//virtualDirectory
	       	for(int i = startRange; i < endRange; ++i) {
	       		String filename = virtualDirectory.getPath() + java.io.File.separator + i + ".tiff";
	       		//System.out.println("writing " + filename);
	       		ImageProcessor img = stack.getProcessor(i-startRange + 1);
	       		new FileSaver(new ImagePlus("", img)).saveAsTiff(filename);
	       		virtualStack.addSlice("" + i + ".tiff");
	       	}
	    }
	
	    IJ.showProgress((float)startIndex/(float)slices);
    }

    
    public void run(ImageProcessor ip) 
    {
    	
    	IJ.showProgress(0);
    	CLContext context = null;
    	
    	try {
    		context = CLContext.create();
    	}catch (RuntimeException e1) {
    		JOptionPane.showMessageDialog(null,
    				"There was a problem detecting drivers. "
    				+ "Please verify the installation of your graphics device's drivers.", 
    				"Driver Error", JOptionPane.ERROR_MESSAGE);
    		return;
    	}catch (java.lang.NoClassDefFoundError e2) {
    		JOptionPane.showMessageDialog(null,
    				"Before running this plugin, please make sure to copy all the dependencies into Fiji's jars folder and restart Fiji.\n"
    				+ "All the necessary dependencies are inside the .zip file of the plugin!", 
    				"Driver Error", JOptionPane.ERROR_MESSAGE);
    		return;
    	}
        CLDevice[] devices = context.getDevices();

        if(devices == null || devices.length == 0) {
        	IJ.log("No acceleration devices found!");
        	//System.out.println("No acceleration devices found!");
            return;
        }

//        ArrayList<CLDevice> input_devices = new ArrayList<CLDevice>();

//        CLDevice maxDevice = context.getMaxFlopsDevice();
//        for(int i = 0; i < devices.length; ++i) {
//            out.println("m: " + devices[i].getName());
////            input_devices.add(devices[i]);
//        }

        PluginDialog pd = new PluginDialog();
        pd.setContext(context);
//        pd.inputDeviceLength = input_devices.size();
        
        if(!pd.run()) {
        	//System.out.println("Not running, releasing context...");
            context.release();
            return;            
        }
        
        FilteringAttributes atts = pd.createAttributes();

        if(atts == null || atts.filters.size() == 0) {
        	IJ.log("Execution pipeline is empty!");
        	//System.out.println("Execution pipeline is empty..");
            return;
        }
        
        //atts.inputDeviceLength = input_devices.size();
        

        inputImage = atts.inputImage;
        
        //isVirtual = pd.useVirtualStack;
        isVirtual = atts.useVirtualStack;
        
        if(isVirtual) {
        	System.out.println("Creating virtual directory");
        	//virtualDirectory = pd.virtualDirectory;
        	virtualDirectory = atts.virtualDirectory;
	        virtualStack = new VirtualStack(inputImage.getWidth(), 
	        								inputImage.getHeight(),
	        								inputImage.getImageStack().getColorModel(),
	        								virtualDirectory.getAbsolutePath());
        }
        		
        long time = nanoTime();

        /// At this point all actions need to be about the same
        /// until this gets fixed..

        int dimension = 2;
        //int overlapAmount = 0;        
        //boolean hasOverlap = false;

        /// do some error checking..
        for(int i = 0; i < atts.filters.size(); ++i)
        {
            JOCLFilter filter = atts.filters.get(i);

            if(i == 0) {
                dimension = filter.getDimensions();
            }
            else if(dimension != filter.getDimensions())
            {
            	System.out.println("Filter dimensions do not match expected " 
                                    + filter.getDimensions() + " got " + dimension);
                return;
            }

            //if(filter.hasMaskOverlap())
            //    hasOverlap = true;
            //overlapAmount = Math.max(overlapAmount, filter.overlapAmount());  
        }

        /*
        /// TODO get max z..
        if(hasOverlap == true) {
            ArrayList<ImageStack> stack = atts.getStructElements();
            for(int i = 0; i < stack.size(); ++i)
                overlapAmount = Math.max(overlapAmount, atts.getStructElementSize(stack.get(i))[2]);
            System.out.println("overlap is: " + overlapAmount);
        }
        */

        //int depthStack = atts.inputImage.getDimensions()[3];
        
        //System.out.println("Devices: " + devices.length);
		//System.out.println("Devices: " + input_devices.size()); Hari version

//        int deviceList = input_devices.size();

            /// override dynamic and split equally..
//        if(atts.chooseConstantDevices == true &&
//        	atts.inputDeviceLength > 0 && atts.inputDeviceLength < devices.length) { //deviceList) {
//        	deviceList = atts.inputDeviceLength;
//        }

        IJ.log("\nUsing " + pd.chosenDevices.size() + " device(s):" );
        for(int i = 0; i < pd.chosenDevices.size(); ++i) {
        	IJ.log(pd.chosenDevices.get(i).getName());
        }
        IJ.log("\nPipeline to be processed:" );
        for(int i = 0; i < atts.filters.size(); ++i) {
        	IJ.log(atts.filters.get(i).getName());
        }
        IJ.log("\n");
        
        RunnableJOCLFilter[] filters = new RunnableJOCLFilter [ pd.chosenDevices.size() ];
        
//        out.println("using total devices: " + deviceList);
//        RunnableJOCLFilter[] filters = new RunnableJOCLFilter [ deviceList ];

        for(int i = 0; i < filters.length; ++i)
        {
            filters[i] = new RunnableJOCLFilter();
            filters[i].rank = i;
            filters[i].context = context;
            filters[i].device = pd.chosenDevices.get(i);
            //filters[i].device = devices[i];
			//filters[i].device = input_devices.get(i); //Hari version
            filters[i].dimension = dimension;            
            filters[i].overlapAmount = atts.overlap.get(pd.chosenDevicesIdxs.get(i));
            filters[i].atts = pd.createAttributes();
            filters[i].index = pd.chosenDevicesIdxs.get(i);
        }


        if(filters.length == 1) 
        {
        	//out.println("Only one!\n");
            filters[0].doJOCLFilter();
        }
        else
        {
            Thread[] threads = new Thread [ filters.length ];

            for(int i = 0; i < threads.length; ++i) {
                threads[i] = new Thread(filters[i]);
                threads[i].start();
//                try {
//					threads[i].join();
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
            }

            try {
                for(int i = 0; i < threads.length; ++i)
                    threads[i].join();        
            }
            catch(Exception e) {
                System.out.println("Failure in parallel threads");
                return;
            }
            

        }

        if(!isVirtual) {
        	/// stitch all results together..
            ImageStack stack = new ImageStack(atts.inputImage.getWidth(), 
            								  atts.inputImage.getHeight());
 
            /// sort the stack by starting slice
	        Collections.sort(stacks);
	
	        for(int i = 0; i < stacks.size(); ++i)
	        {
	            StackRange sr = stacks.get(i);
	            //out.println("adding: " + sr.startRange + " " + sr.endRange + " type: " + sr.type + " took: " + sr.time);
	            for(int j = 0; j < sr.stack.getSize(); ++j)
	                stack.addSlice(sr.stack.getProcessor(j+1));
	        }
	        
	        //out.println("ending..");
	        String title = atts.inputImage.getShortTitle() + "_" + atts.filters.get(atts.filters.size()-1).getName() + "Filter";
	        if(atts.enableZorder)
	            title += "(Zorder)";
	        
	        ImagePlus outputImage = new ImagePlus(title, stack);
	        outputImage.show();
        } else {
        	String title = atts.inputImage.getShortTitle() + "_" + atts.filters.get(atts.filters.size()-1).getName() + "Filter";
	        if(atts.enableZorder)
	            title += "(Zorder)";
	        
	        ImagePlus outputImage = new ImagePlus(title, virtualStack);
	        outputImage.show();
        }

        try {
            context.release();    
        }catch(Exception e){
            out.println("Unable to release context");
        }

        time = nanoTime() - time;
        IJ.log("Total: computation took: "+(time/1000000.0)+"ms");
    }

    public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}
		
    	inputImage = imp;
        return DOES_ALL; //DOES_8G;
    }
    
	public void showAbout() {
		IJ.showMessage("QuantCTFiltering (JOCL)",
			"An OpenCL accelerated image processing library.."
		);
	}

    /*
     * Classes for gray-scale morphology of images using flat structuring elements
     */

    class RunnableJOCLFilter implements Runnable 
    {
                  
        CLContext context = null;
        CLDevice device = null;

        int rank = -1;
        int dimension = -1;
        int overlapAmount = -1;
        int index = 0;

        FilteringAttributes atts = null;
        CLAttributes clattr = null;
       
        @Override
        public void run() {
        	doJOCLFilter();
        }

/*
        private void initializeData() 
        {
            clattr.initializeData(dimension, atts.inputImage, atts, overlapAmount);

            if(atts.enableZorder) {
                clattr.zorder.zIbits.getBuffer().position(0);
                clattr.zorder.zJbits.getBuffer().position(0);
                clattr.zorder.zKbits.getBuffer().position(0);

                clattr.queue.putWriteBuffer(clattr.zorder.zIbits, false);
                clattr.queue.putWriteBuffer(clattr.zorder.zJbits, false);
                clattr.queue.putWriteBuffer(clattr.zorder.zKbits, false);
            }
        }
*/

/*
        private void cleanupData() 
        {
            clattr.inputBuffer.release();
            clattr.outputBuffer.release();
            if(clattr.outputTmpBuffer != null)
                clattr.outputTmpBuffer.release();
        }
*/
        private void doJOCLFilter() 
        {
            CLAttributes clattr = new CLAttributes();
            clattr.rank = rank;
            clattr.context = context;
            clattr.device = device;
            clattr.queue = clattr.device.createCommandQueue();
 
            String deviceType = device.getName() + "(" + device.getType().toString() + ")";
            //initializeData();

            clattr.initializeData(dimension, atts.inputImage, atts, atts.overlap.get(index), atts.maxSliceCount.get(index));//overlapAmount);

            if(atts.enableZorder) {
                clattr.zorder.zIbits.getBuffer().position(0);
                clattr.zorder.zJbits.getBuffer().position(0);
                clattr.zorder.zKbits.getBuffer().position(0);

                clattr.queue.putWriteBuffer(clattr.zorder.zIbits, false);
                clattr.queue.putWriteBuffer(clattr.zorder.zJbits, false);
                clattr.queue.putWriteBuffer(clattr.zorder.zKbits, false);
            }


            int overlap = (int) (atts.overlap.get(index)/2);
            //ImageStack imagestack = new VirtualStack(atts.width, atts.height,, );
     
            //System.out.println("Processing " + atts.filters.size() + " filter(s)!");
            
          /// special case for pipeline of 1...
            if(atts.filters.size() == 1) 
            {
                //boolean first = true;
                JOCLFilter ftype = atts.filters.get(0);
                JOCLFilter filter = ftype.newInstance();
                filter.fromString(ftype.toString());
                filter.processFilterWindowComponent();
                
                //out.println("dims: " + atts.width + " " + atts.height + " " + atts.slices + " " + atts.channels);
                
                int[] stackRange = new int [2];
                while(getNextRange(stackRange, atts.maxSliceCount.get(index))) 
                {
                    //if(first == true) {
                        //filter.setAttributes(clattr, atts);
                        //filter.loadKernel();
                        //first = false;
                    //}
                	
//                	System.out.println("index: " + index);
//                	System.out.println("overlap: " + overlap);
                	
                	filter.setAttributes(clattr, atts,index);
                    filter.loadKernel();
                	
                    clattr.loadNextData(atts.inputImage.getStack(), atts, stackRange[0], stackRange[1], overlap);
                    atts.maxSliceCount.set(index, (stackRange[1]-stackRange[0]));
                    //if(stackRange[0] > 0) 
                    //    atts.maxSliceCount += 1;

                    long time = System.nanoTime();        
                    filter.runFilter();
                    time = (long) ((System.nanoTime() - time)/1000000.0);
                    //out.println("rank: " + rank + " " + stackRange[0] + " " + stackRange[1]);
                    //System.out.println("Filter ran in: " + time);
                    ImageStack imagestack = new ImageStack(atts.width, atts.height);
                    clattr.writeNextData(imagestack, atts, stackRange[0], stackRange[1], overlap);
                    
                    //ImagePlus outputImagetmp = new ImagePlus("Temp", imagestack);
					//outputImagetmp.show();
                    
                    addResultStack(stackRange[0], stackRange[1], imagestack, deviceType, time);
                                   
                    filter.releaseKernel();
                    
                }

                //if(first == false) {
                //    filter.releaseKernel();
                //}
                
            }
            else {

                int[] stackRange = new int [2];
                while(getNextRange(stackRange, atts.maxSliceCount.get(index))) 
                {
                    //out.println("rank: " + rank + " " + stackRange[0] + " " + stackRange[1]);
                    clattr.loadNextData(atts.inputImage.getStack(), atts, stackRange[0], stackRange[1], overlap);
                    atts.maxSliceCount.set(index, (stackRange[1]-stackRange[0]));

                    /// read data...
                    long pipelineTime = 0;
                    for(int i = 0; i < atts.filters.size(); ++i)
                    {
                    	JOCLFilter ftype = atts.filters.get(i);
                    	JOCLFilter filter = ftype.newInstance();
                    	filter.fromString(ftype.toString());
                    	filter.processFilterWindowComponent();

                        //out.println("dims: " + atts.width + " " + atts.height + " " + atts.slices + " " + atts.channels);

                        filter.setAttributes(clattr, atts,index);
                        filter.loadKernel();

                        long time = System.nanoTime();        
                        filter.runFilter();
                        time =  (long)((System.nanoTime() - time)/1000000.0);
                        pipelineTime += time;

                       // System.out.println("Filter ran in: " + time);        
                        
                        
                        //if(atts.intermediateSteps) res_image.show();
                        /// swap input and output..
                        if(i < atts.filters.size()-1) {
                            clattr.swapBuffers();
                        }
                        
                        filter.releaseKernel();
                    }

                    ImageStack imagestack = new ImageStack(atts.width, atts.height);
                    clattr.writeNextData(imagestack, atts, stackRange[0], stackRange[1], overlap);
                    addResultStack(stackRange[0], stackRange[1], imagestack, deviceType, pipelineTime);
                    
                    ImagePlus outputImagetmp = new ImagePlus("Temp", imagestack);
					outputImagetmp.show();
                }
                
            }

            //cleanupData();
            clattr.inputBuffer.release();
            clattr.outputBuffer.release();
            if(clattr.outputTmpBuffer != null && !clattr.outputTmpBuffer.isReleased())
                clattr.outputTmpBuffer.release();
        } 
            
           
            
//            int[] stackRange = new int [2];
//            while(getNextRange(stackRange, atts.maxSliceCount)) 
//            {
//                out.println("rank: " + rank + " " + stackRange[0] + " " + stackRange[1]);
//                clattr.loadNextData(atts.inputImage.getStack(), atts, stackRange[0], stackRange[1], overlap);
////                atts.maxSliceCount = (stackRange[1]-stackRange[0]) + 1;   // That was causing troubles! OUT_OF_RESOURCES error
//                atts.maxSliceCount = stackRange[1]-stackRange[0];
//
//                /// read data...
//                long pipelineTime = 0;
//                for(int i = 0; i < atts.filters.size(); ++i)
//                {
//                    JOCLFilter filter = atts.filters.get(i);
//
//                    out.println("Processing: " + filter.getName());
//                    out.println("dims: " + atts.width + " " + atts.height + " " 
//                                         + atts.slices + " " + atts.channels);
//
//                    filter.setAttributes(clattr, atts);
//                    filter.loadKernel();
//
//                    long time = System.nanoTime();        
//                    filter.runFilter();
//                    time =  (long)((System.nanoTime() - time)/1000000.0);
//                    pipelineTime += time;
//
//                    System.out.println("Filter ran in: " + time);        
//                    
//                    filter.releaseKernel();
//                }
//
//                ImageStack imagestack = new ImageStack(atts.width, atts.height);
//                clattr.writeNextData(imagestack, atts, stackRange[0], stackRange[1], overlap);
//                addResultStack(stackRange[0], stackRange[1], imagestack, deviceType, pipelineTime);
//            }
//
//            //cleanupData();
//            clattr.inputBuffer.release();
//            clattr.outputBuffer.release();
//            if(clattr.outputTmpBuffer != null && !clattr.outputTmpBuffer.isReleased())
//                clattr.outputTmpBuffer.release();
//        }
    }
}
