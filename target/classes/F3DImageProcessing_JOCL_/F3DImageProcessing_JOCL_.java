package F3DImageProcessing_JOCL_;

import static com.jogamp.opencl.CLMemory.Mem.READ_WRITE;
import static java.lang.System.nanoTime;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.IJ;
import ij.VirtualStack;
import ij.io.FileSaver;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLPlatform;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Collections;

import javax.swing.JOptionPane;

public class F3DImageProcessing_JOCL_ implements ExtendedPlugInFilter
{

	/**!
	 * Register all filters supported by F3D..
	 */
	static {
	  JOCLFilter.registerFilter(new BilateralFilter());
	  JOCLFilter.registerFilter(new MedianFilter());
	  JOCLFilter.registerFilter(new MMFilterEro());
	  JOCLFilter.registerFilter(new MMFilterDil());
	  JOCLFilter.registerFilter(new MMFilterOpe());
	  JOCLFilter.registerFilter(new MMFilterClo());
	  JOCLFilter.registerFilter(new MaskFilter());
	  JOCLFilter.registerFilter(new FFTFilter());
	}
	
	
	/**!
	 * Stack range allows stacks to processed in parallel over 
	 * separate ranges
	 * @author Hari
	 *
	 */
	public class StackRange implements Comparable<StackRange>
	{
		long time; //time taken to process
		int startRange; //start index within overall stack.
		int endRange; //end index within overall stack
		String type; //type of accelerator 
		ImageStack stack; //imagestack containing the data.

		/**!
		 * allow for sorting after all imagestacks have been processed.
		 * @param sr Range for image stack
		 */
		public int compareTo(StackRange sr) {
			/// ascending
			return startRange - sr.startRange;
		}
	};

    private PluginDialog pd;
    private CLContext[] contexts;
    private FilteringAttributes atts;
    private VirtualStack virtualStack = null;
    
    private int startIndex = 0;
    private ImagePlus current;
    
    F3DMonitor monitor = new F3DMonitor();
    ArrayList<StackRange> stacks = new ArrayList<StackRange>();

	//Debugging..
    /**!
     * Test main function to help exercise functionality.
     */
    public static void main(String[] args) {
    	
        // set the plugins.dir property to make the plugin appear in the Plugins menu   	
        Class<?> clazz = F3DImageProcessing_JOCL_.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);
        
        /**!
         * Start ImageJ and ensure application ends when the
         * window is closed.
         */
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
		
		/**!
		 * run the plugin.
		 */
//        ImagePlus image1 = IJ.openImage("http://imagej.nih.gov/ij/images/bat-cochlea-renderings.zip");
//		image1.show();
//		ImagePlus image2 = IJ.openImage("http://vis.lbl.gov/~daniela/data/numberedSlices.tif");
//        image2.show();
//        IJ.runPlugIn(clazz.getName(), "");
    }

    /**!
     * Obtains next range of data to be processed
     * @param range - set start and end range available for accelerator
     * @param sliceCount - count requested by accelerator
     * @return whether there is a next image
     */
    public synchronized boolean getNextRange(int[] range, int sliceCount) {
        int endIndex = current.getDimensions()[3];

        /**! 
         *   if startIndex of inputImage is past end
         *   then no more data is left.
         */
        if(startIndex >= endIndex)
            return false;
	
//        System.out.println("maxSliceCount: "  + sliceCount);
        range[0] = startIndex;
        range[1] = startIndex + sliceCount;
        
        if(range[1] >= endIndex)
            range[1] = endIndex;
	
        /// update location of range
        startIndex = range[1];
        return true;
    }
    
    /**!
     * When accelerator finishes it adds itself to the final ArrayList to be reconstructed into
     *        an ImagePlus object.
     * @param startRange   Starting slice index 
     * @param endRange     End slice index
     * @param stack        The image stack object containing the slices.
     * @param type         The accelerator type.
     * @param time         The amount of time taken by the accelerator.
     * 
     */
    public synchronized void addResultStack(int startRange, 
    										int endRange, 
    										ImageStack stack, 
    										String type, 
    										long time) {
    	int slices = current.getDimensions()[3];
	
	    StackRange sr = new StackRange();
	    sr.startRange = startRange;
	    sr.endRange = endRange;
	    sr.stack = stack;
	    sr.type = type;
	    sr.time = time;
	
	    if(!pd.useVirtualStack) {
	    	stacks.add(sr);
	    } else {       	
	      	//virtualDirectory
	       	for(int i = startRange; i < endRange; ++i) {
	       		String filename = pd.virtualDirectory.getPath() + java.io.File.separator + i + ".tiff";
	       		System.out.println("writing " + filename);
	       		ImageProcessor img = stack.getProcessor(i-startRange + 1);
	       		new FileSaver(new ImagePlus("", img)).saveAsTiff(filename);
	       		virtualStack.addSlice("" + i + ".tiff");
	       	}
	    }
	
	    IJ.showProgress((float)startIndex/(float)slices);
    }

    /**!
     * Overriding from ExtendedPlugInFilter, shows the main dialog for the plugin
     */
    @Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    	
    	String drivers_exception="";
    	String deps_exception="";
    	
    	CLPlatform[] platforms = CLPlatform.listCLPlatforms();
    	ArrayList<CLDevice[]> devices_tmp = new ArrayList<CLDevice[]>();
    	
    	for (int i=0;i<platforms.length;i++){
    		devices_tmp.add(platforms[i].listCLDevices());
    	}

    	/**!
    	 * Try to create a context and determine whether the Kernel will startup properly.
    	 */
    	contexts = new CLContext[devices_tmp.size()];
    	
    	CLDevice[] devices = null;
    	if (devices_tmp.size()==1) {
    		devices = devices_tmp.get(0);
    		contexts[0] = CLContext.create(devices_tmp.get(0));
    	}
    	else {
	    	for (int i=0;i<devices_tmp.size()-1;i++){
	    		devices = ArrayUtils.addAll(devices_tmp.get(i), devices_tmp.get(i+1));
	    		try {
	    			contexts[i] = CLContext.create(devices_tmp.get(i));
	    			contexts[i+1] = CLContext.create(devices_tmp.get(i+1));
	    		} catch (RuntimeException e1) {
	    			JOptionPane.showMessageDialog(null,
	        				"There was a problem detecting drivers. "
	        				+ "Please verify the installation of your graphics device's drivers.", 
	        				"Driver Error", JOptionPane.ERROR_MESSAGE);
	    			StringWriter errors = new StringWriter();
	    			e1.printStackTrace(new PrintWriter(errors));
	        		drivers_exception = errors.toString()+"\n"+e1.getMessage()+"\n";
	    		} catch (java.lang.NoClassDefFoundError e2) {
	        		JOptionPane.showMessageDialog(null,
	        				"Before running this plugin, please make sure to copy all the dependencies into Fiji's jars folder and restart Fiji.\n"
	        				+ "All the necessary dependencies are inside the .zip file of the plugin!", 
	        				"Driver Error", JOptionPane.ERROR_MESSAGE);
	        		StringWriter errors = new StringWriter();
	    			e2.printStackTrace(new PrintWriter(errors));
	        		deps_exception = errors.toString()+"\n"+ e2.getMessage()+"\n";
	        		return DONE;
	    		}
	    	}
    	}
    	    	
    	monitor.setKeyValue("drivers.exception", drivers_exception);
    	monitor.setKeyValue("deps.exception", deps_exception);
    	
    	/**!
    	 * Determine which OpenCL were discovered and ensure that it is not
    	 * null or 0.
    	 */
        /*CLDevice[] devices = context.getDevices();

        if(devices == null || devices.length == 0) {
        	IJ.log("No acceleration devices found!");
        	//System.out.println("No acceleration devices found!");
            return;
        }*/


        /**!
         * Show Dialog to user to construct appropriate filters
         * and parameters needed for execution.
         */

        pd = new PluginDialog(monitor, this);
        pd.setDevices(devices);
        
        if(!pd.run()) {
        	for (int i=0;i<contexts.length;i++)
        		contexts[i].release(); /**! if canceled then release context and return */
        	return DONE;            
        }
        
        return 1;
	}
    
    /**!
     * Main image plus run function. This is the entry point that Fiji calls to run the F3D plugin.
     * @param ip input image.
     */
    public void run(ImageProcessor ip, ActionEvent event) 
    {    	
    	/// Set progress bar at 0%
    	IJ.showProgress(0);
    	
    	/**!
         * Get attributes set by user.
         */
        atts = pd.createAttributes();
        if (atts == null) {
        	IJ.log("Attributes are null!");
            return;
        }
        
        if (event!=null && event.getActionCommand().equals("Preview")) {
        	current = pd.previewInput;
        } else {
        	current = pd.activeInput;
        }
        
        /**!
         * If virtual stack is selected then create and use the appropriate structure 
         */
        if(pd.useVirtualStack) {
        	System.out.println("Creating virtual directory");
        	
        	virtualStack = new VirtualStack(current.getWidth(),
        									current.getHeight(),
        									current.getImageStack().getColorModel(),
        									pd.virtualDirectory.getAbsolutePath());
        
        }

        
        /**!
         * Start tracking time.
         */
        long time = nanoTime();

        /**!
         * Print status output.
         */
        {
	        IJ.log("\nUsing " + pd.chosenDevices.size() + " device(s):" );
	        
	        for(int i = 0; i < pd.chosenDevices.size(); ++i) {
	        	IJ.log(pd.chosenDevices.get(i).getName());
	        }
	        
	        IJ.log("\nPipeline to be processed:" );
	        for(int i = 0; i < atts.pipeline.size(); ++i) {
	        	IJ.log(atts.pipeline.get(i).getName());
	        }
	        IJ.log("\n");
	        
        }

        
        /**!
         * Execute filters. Create one thread per device.
         */
        
        RunnableJOCLFilter[] filters = new RunnableJOCLFilter [ pd.chosenDevices.size() ];
        
        for(int i = 0; i < filters.length; ++i)
        {
            filters[i] = new RunnableJOCLFilter();
            filters[i].rank = i;
            filters[i].device = pd.chosenDevices.get(i);
            //filters[i].dimension = dimension;        
            filters[i].overlapAmount = atts.overlap.get(pd.chosenDevicesIdxs.get(i));
            filters[i].atts = pd.createAttributes();
            filters[i].index = pd.chosenDevicesIdxs.get(i);
        }


        /**!
         * If only one device is available then skip thread creation and execute.
         */
        if(filters.length == 1) {
        	filters[0].doJOCLFilter();
        }
        else
        {
            Thread[] threads = new Thread [ filters.length ];

            for(int i = 0; i < threads.length; ++i) {
                threads[i] = new Thread(filters[i]);
                threads[i].start();
            }

            /**!
             * Wait for all threads to finish before continuing.
             */
            try {
                for(int i = 0; i < threads.length; ++i)
                    threads[i].join();        
            }
            catch(Exception e) {
                System.out.println("Failure in parallel threads");
                return;
            }
        }

        /**!
         * All threads are done! Now reconstruct final image.
         */
        
    	/// create descriptive title
        String title;
        title = pd.activeInput.getShortTitle() + "_" +
        		atts.pipeline.get(atts.pipeline.size()-1).getName();
        
    	/// create new stack
        if(!pd.useVirtualStack) {
        	/// stitch all results together...
       		ImageStack stack = new ImageStack(current.getWidth(), 
       							   			  current.getHeight());

            /// sort the stack by starting slice
	        Collections.sort(stacks);
	
	        /// add slices to output stack.
	        for(int i = 0; i < stacks.size(); ++i)
	        {
	            StackRange sr = stacks.get(i);
	            for(int j = 0; j < sr.stack.getSize(); ++j) {
	                stack.addSlice(sr.stack.getProcessor(j+1));
	            }
	        }
	        
	        /// Show output image. If preview is selected, shows only in the dialog.
	        if (event!=null && event.getActionCommand().equals("Preview")) {
	        	pd.previewResult.setStack(stack);
	        } else {
	        	ImagePlus outputImage = new ImagePlus(title, stack);
	        	outputImage.show();
	        }
        } else {
	        /// set virtualStack that has already been saved as files to disk.
	        /// this should use filenames to render in sorted order.
	        /// TODO: confirm that an additional sort is not necessary.
        	if (event!=null && event.getActionCommand().equals("Preview")) {
	        	pd.previewResult.setStack(virtualStack);
	        } else {
	        	ImagePlus outputImage = new ImagePlus(title, virtualStack);
	        	outputImage.show();
	        }
        }

        /**! Print amount taken */
        time = nanoTime() - time;
        IJ.log("Total computation took: "+(time/1000000.0)+"ms");
        
        double finaltime = time/1000000.0;
        monitor.setKeyValue("comp.time",Double.toString(finaltime));
        monitor.writeToLog();
        
        startIndex=0;
        
    }
    
    public void run(ImageProcessor ip) 
    {
    	this.run(ip, null);
    }

    /**!
     * Tells Fiji what type of input this plugin can handle. Currently this
     * is set to handle ALL. Also shows About message and sets input image appropriately.
     */
    public int setup(String arg, ImagePlus imp) {
    	
    	/// Fiji is asking for details about this plugin.
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}
		
    	//NOTE: Assignment never really uses this: inputImage = imp;
        return DOES_ALL; //DOES_8G;
    }
    
    /**!
     * Show about message.
     * TODO: Added details about each filter.
     */
	public void showAbout() {
		IJ.showMessage("F3D Image Processing (JOCL)",
			"An OpenCL accelerated image processing library.."
		);
	}

    /**!
     * Classes for gray-scale morphology of images using flat structuring elements
     */
    class RunnableJOCLFilter implements Runnable 
    {
    	FilteringAttributes atts = null;
        CLAttributes clattr = null;
        
        CLContext context = null;
        CLDevice device = null;

        int rank = -1;
        //int dimension = -1;
        int overlapAmount = -1;
        int index = 0;

       
        /**!
         * The main run function that performs JOCL filtering in a separate thread.
         */
        @Override
        public void run() {
        	doJOCLFilter();
        }

        private void doJOCLFilter() 
        {       	
        	/**!
        	 * Create CLAttributes class to consolidate CL parameters.
        	 * Set rank, context, and device, and each device creates a unique command queue.
        	 */
            clattr = new CLAttributes();
            //clattr.rank = rank; 
            clattr.context = device.getContext();
            clattr.device = device;
            clattr.queue = clattr.device.createCommandQueue();
 
            /// construct string to represent underlying device that is capable of processing filter.
            String deviceType = device.getName() + "(" + device.getType().toString() + ")";
            
        	int maxOverlap = 0;
            for(int i = 0; i < atts.pipeline.size(); ++i)
            {
                JOCLFilter filter = atts.pipeline.get(i);
                maxOverlap = Math.max(maxOverlap, filter.getInfo().overlapZ);
            }
            
            /**!
             * IMPORTANT: This function initialize the data on the device.
             * TODO: this class needs to better handle overlaps across multiple filters
             * and needs to be better at allocating memory.
             */
            clattr.initializeData(current, 
            					  atts, 
            					  maxOverlap, 
            					  atts.maxSliceCount.get(index));

            /**!
             * Initialize Temp memory buffer if pipeline needs it.
             */
            
        	for(int i = 0; i < atts.pipeline.size(); ++i)
        	{
        		if(atts.pipeline.get(i).getInfo().useTempBuffer) {
        			
        			clattr.outputTmpBuffer = 
        					clattr.context.createByteBuffer(clattr.inputBuffer.getBuffer().capacity(), READ_WRITE);
        			break;
        		}
        	}
        	
            /**!
             * Executing Kernel from center of the stack leaves max(0,startSlice - overlap/2) 
             * from previous stack and min(lastSlice, endSlice + overlap/2)
             */
//            int overlap = (int) (atts.overlap.get(index)/2);
//            for(int i = 0; i < atts.overlap.size(); ++i) {
//            	System.out.println(atts.overlap.get(i) + " overlap");
//            }
        	    
            /**!
             * The filters create a copy of the main workflow.
             */
            int[] stackRange = new int [2];
            /// Get next available range in the pipeline that requires processing.
            stacks.clear();
        	while(getNextRange(stackRange, atts.maxSliceCount.get(index))) 
            {
        		atts.sliceStart = stackRange[0];
        		atts.sliceEnd = stackRange[1];
        		
        		/// copy data from CPU to accelerator.
            	clattr.loadNextData(current.getStack(), atts, stackRange[0], stackRange[1], maxOverlap);
            	atts.maxSliceCount.set(index, (stackRange[1]-stackRange[0]));
            	atts.overlap.set(index, maxOverlap);
            	/// read data...
            	long pipelineTime = 0;

            	for(int i = 0; i < atts.pipeline.size(); ++i)
            	{
            		JOCLFilter filter = atts.pipeline.get(i).clone();

            		/// set attributes within the filter.
            		filter.setAttributes(clattr, atts, monitor, index);

            		/// set up the kernel for execution.
            		if (!filter.loadKernel())
            		{
            			monitor.writeToLog();
            			return;
            		}

            		/// start timing the filter.
            		long time = System.nanoTime(); 
            		
            		if (!filter.runFilter())
            		{
            			monitor.writeToLog();
            			return;
            		}
            		
            		/// execution end time.
            		time =  (long)((System.nanoTime() - time)/1000000.0);
            		
            		/// overall time 
            		pipelineTime += time;


            		/// if there is another kernel to run swap input and output..
            		if(i < atts.pipeline.size()-1) {
            			clattr.swapBuffers();
            		}

            		/// release kernel.
            		filter.releaseKernel();
            	}
                    

            	/**!
            	 *  save output of imagestack after pipeline execution to the final result stack
            	 */
            	
            	ImageStack imagestack = new ImageStack(atts.width, atts.height);
            	clattr.writeNextData(imagestack, atts, stackRange[0], stackRange[1], maxOverlap);
            	addResultStack(stackRange[0], stackRange[1], imagestack, deviceType, pipelineTime);
            }

            /**!
        	 * Clean up data
        	 */
            clattr.inputBuffer.release();
            clattr.outputBuffer.release();
            if(clattr.outputTmpBuffer != null && !clattr.outputTmpBuffer.isReleased())
                clattr.outputTmpBuffer.release();
            
        } 
    }


	@Override
	public void setNPasses(int nPasses) {
		// TODO Auto-generated method stub
		
	}
}
