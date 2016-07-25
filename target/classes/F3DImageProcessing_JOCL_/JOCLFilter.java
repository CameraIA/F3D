package F3DImageProcessing_JOCL_;

import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

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

import ij.ImageStack;

/**!
 * Abstract class that controls the API of all F3D Filters.
 * @author hari
 *
 */
public abstract class JOCLFilter
{	
	/**!
	 * Currently supported types (Byte & Float data)
	 * @author hari
	 *
	 */
	public enum Type {
		Byte, Float
	}
	
	/**!
	 * Information about each filter. This includes the filter name,
	 * amount of overlap in X, Y, and Z directions, the memory type needed
	 * by the filter and whether a temporary buffer is needed. 
	 * @author hari
	 *
	 */
	public class FilterInfo 
	{
		String name = ""; /// String name of filter.
		int overlapX = 0; /// overlap in X
		int overlapY = 0; /// overlap in Y
		int overlapZ = 0; /// overlap in Z
		Type memtype = Type.Byte; /// memory type used by filter 
		boolean useTempBuffer = false;
	}
	
	/**!
	 * Helper Panel to create widget used by many filters in F3D.
	 * Support selections of mask images.
	 * @author hari
	 *
	 */
	public class FilterPanel
	{
		public int L = -1; /// index to determine diagnoal length.
		public String maskImage = ""; /// name of mask image
		ArrayList<ImageStack> maskImages = null; ///imagestacks constructed based on user input.
		
		/**!
		 *  convert panel input to JSON
		 * @return serialized string of filter panel.
		 */
		public String toJSONString() {
			String result = "[{"
							+ "\"maskImage\" : \"" + maskImage + "\""
							+ (maskImage.startsWith("StructuredElementL") ? ", \"maskLen\" : \"" + L + "\"" : "")
							+ "}]";
			return result;
		}
		
		/**!
		 * convert from JSON to panel parameters..
		 * @param str - serialized input
		 */
		public void fromJSONString(String str) {
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
		
		/**!
		 * Initialize interface for user input.
		 * @return JPanel interface that will be embedded by PluginDialog.
		 */
		public JPanel setupInterface() 
	    {
			JPanel panel = new JPanel();	
	        JLabel label = new JLabel("Mask:");
	        
	        //fiji complains when I use JComboBox<String>
	        
	        @SuppressWarnings({ "rawtypes", "unchecked" }) 
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
	
		/**!
		 * Apply and secondary effects that might need to happen after UI has finished.
		 * The main affect is creating actual image masks from the UI selection.
		 */
		void processFilterWindowComponent() {
			maskImages = FilteringAttributes.getStructElements(maskImage, L);
		}
	};

	FilterPanel filterPanel = new FilterPanel(); ///a default UI widget for each filter
	F3DMonitor monitor; ///monitor support
	
	CLAttributes clattr; //OpenCL attributes
    FilteringAttributes atts; //Filter attributes
    int index; //index of widget

	public static ArrayList<JOCLFilter> filters = new ArrayList<JOCLFilter>(); ///static list of filters
    /**!
     * register new filter to static filter list.
     * @param j
     */
	static void registerFilter(JOCLFilter j) {
		filters.add(j);
	}
	

    /**!
     * get filter information
     * @return information about filter;
     */
    abstract FilterInfo getInfo();
    
    /**!
     * get name of filter (convenience function)
     * @return name of filter
     */
    abstract String getName();
	
    /// run kernels.
    /**!
     * load the kernel
     * @return success or failure of kernel load.
     */
    abstract boolean loadKernel();
    
    /**!
     * run the filter
     * @return success of failure of kernel execution
     */
    abstract boolean runFilter();
    
    /**!
     * release kernel resources.
     * @return success or failure of resource cleanup
     */
    abstract boolean releaseKernel();
    
    /// show and process panel..
    /**!
     * access function to generate custom UI for kernel.
     * @return user interface for kernel.
     */
    abstract Component getFilterWindowComponent();
    
    /**!
     * helper function to invoke post processing UI elements when dialog is closed.
     */
    abstract void  processFilterWindowComponent();
	
	/// create new instance of the component..
    /**!
     * clone new instance of kernel.
     * @return clone a new instance of the kernel.
     */
    abstract JOCLFilter newInstance();
	
	/// serialize and de-serialize each component
    /**!
     * Serialize Kernel to JSON string
     * @return
     */
	abstract String toJSONString();
	
	/**!
	 * Convert from JSON string to Kernel object.
	 * @param str
	 */
	abstract void fromJSONString(String str);

	/**!
	 * Set attributes for filter execution.
	 * @param c - opencl attributes 
	 * @param a - filter attributes
	 * @param m - monitor
	 * @param idx - index of filter in pipeline.
	 */
	public void setAttributes(CLAttributes c, FilteringAttributes a, F3DMonitor m, int idx)
    {
        clattr = c;
        atts = a;
        index = idx;
        monitor = m;
    }
	
	/**!
	 * clone function to create a copy of current instance of kernel.
	 */
    public JOCLFilter clone() {
    	
        JOCLFilter filter = newInstance();
        
        filter.fromJSONString(toJSONString());
        filter.processFilterWindowComponent();
    
        return filter;
    }	
}

