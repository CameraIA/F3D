package F3DImageProcessing_JOCL_;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.ColorModel;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.jogamp.opencl.CLDevice;

import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.plugin.frame.Recorder;
import ij.process.ImageProcessor;
/**!
 * The main user interface for the F3D plugin.
 * 1. Enables user to create a custom workflow of F3D filters.
 * 2. Selection of OpenCL accelerators
 * 3. Whether or not to write to memory or virtual directory.
 * 4. Limit number of resources (such as slices) available to each resource.
 * 
 * @author hari
 * @author tperciano
 */
public class PluginDialog implements AdjustmentListener
{
	GenericDialog gd;
	FilteringAttributes gatts;
	
	public HashMap<String, JOCLFilter> filterList;
	int maxNumDevices;
	CLDevice[] devices;
	
	ArrayList<CLDevice> chosenDevices;
	ArrayList<Integer> chosenDevicesIdxs;

	JPanel filterPanel;
	JPanel devicePanel;
	JPanel previewPanel;
	ImagePlus activeInput;
	ImagePlus previewInput = null;
	ImagePlus previewResult = null;
	JOCLFilter activeFilter;
	String[] types = null;
	JComboBox<String> inputBox;
	JComboBox<String> typeBox;
	ArrayList<JSpinner> devicesSpList;
	F3DImageProcessing_JOCL_ filter;
	Scrollbar sliceSelector;
	ImageCanvas imageCanvas;
	JPanel previewJPanel;
	JButton previewb;
	
	DefaultTableModel model;
	JTable table;
	F3DMonitor monitor;
	
	boolean useVirtualStack;
	File virtualDirectory;

	/**
	 * @param monitor F3D monitor
	 */
	PluginDialog(F3DMonitor monitor, F3DImageProcessing_JOCL_ filter) {
		this.monitor = monitor;
		this.filter = filter;
		chosenDevices = new ArrayList<CLDevice>();
		chosenDevicesIdxs = new ArrayList<Integer>();

		/**
		 * create instance of dialog class.
		 * each part of the dialog has a different purpose.
		 */
		gd = new GenericDialog("F3D -- Fast 3D Non-Linear Filtering");
		filterPanel = new JPanel();
		devicePanel = new JPanel();
		previewPanel = new JPanel();
		
		/**
		 * the initial filter attribute.
		 */
		gatts = new FilteringAttributes();
		activeFilter = null;
		activeInput = null;
		
		/**!
    	 * List all filters available
    	 */
		
		filterList = new HashMap<String, JOCLFilter>();
		for(int i = 0; i < JOCLFilter.filters.size(); ++i){
			filterList.put(JOCLFilter.filters.get(i).getName(), 
					       JOCLFilter.filters.get(i));
		}
		types = new String[filterList.keySet().size()];
		types = filterList.keySet().toArray(types);
		java.util.Arrays.sort(types);
		
	}

	/**
	 * set all the available devices.
	 * @param devices
	 */
	public void setDevices(CLDevice[] devices) {
		this.devices = devices;
		maxNumDevices = devices.length;
	}
	
	/**
	 * Creates top panel for the GUI
	 * @return Top awt panel
	 */
	public Panel createTopPanel() {
		Panel top = new Panel();
		
		JPanel topJPanel = new JPanel();
		topJPanel.setLayout(new BoxLayout(topJPanel, BoxLayout.X_AXIS));
		
		//Create two main panels horizontally
		topJPanel.add(createLeftPanel());
		topJPanel.add(createPreviewerPanel());
		
		top.add(topJPanel);
		return top;
	}
	
	/**
	 * Creates left part of top panel for the GUI
	 * @return Left top awt panel
	 */
	public Panel createLeftPanel() {
		Panel left = new Panel();

		JPanel leftJPanel = new JPanel();
		leftJPanel.setLayout(new BoxLayout(leftJPanel, BoxLayout.Y_AXIS));

		leftJPanel.add(createHeader());
		leftJPanel.add(createWorkflow());
		leftJPanel.add(createFooter());
		selectedItemChanged(types[0]);
		leftJPanel.add(createFooterDevices());

		left.add(leftJPanel);

		return left;
	}
	
	/**
	 * Creates header panel for the GUI
	 * @return Header awt panel
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Panel createHeader() {
		Panel header = new Panel();

		JPanel headerJPanel = new JPanel();
		headerJPanel.setLayout(new GridLayout(2, 2, 0, 0));

		JLabel inputLabel = new JLabel("Input");

		inputBox = new JComboBox(
				FilteringAttributes.getImageTitles(false));

		activeInput = WindowManager.getImage((String) inputBox
				.getSelectedItem());

		inputBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
					activeInput = WindowManager.getImage((String) e.getItem());
					selectedItemChangedInput();
					cleanWorkflowTable();
					updatePreviewInput();
					//TODO Figure out a better way to update the panels
					previewJPanel.remove(imageCanvas);
					previewJPanel.remove(sliceSelector);
					previewJPanel.remove(previewb);
					imageCanvas = new ImageCanvas(previewResult);
			        imageCanvas.setVisible(true); 
			        previewJPanel.add(imageCanvas, BorderLayout.CENTER);
					previewJPanel.add(sliceSelector, BorderLayout.SOUTH);
					previewJPanel.add(previewb);
			        previewJPanel.revalidate();
			        previewJPanel.repaint();
				}
			}
		});

		JLabel algorithmLabel = new JLabel("Algorithm");
		typeBox = new JComboBox(types);
		typeBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
					selectedItemChanged((String) e.getItem());
				}
			}
		});

		headerJPanel.add(inputLabel);
		headerJPanel.add(inputBox);

		headerJPanel.add(algorithmLabel);
		headerJPanel.add(typeBox);

		header.add(headerJPanel);

		return header;
	}

	/**
	 * Creates workflow panel for the GUI
	 * @return Workflow awt panel
	 */
	public Panel createWorkflow() {
		Panel workflow = new Panel();
		workflow.setLayout(new BorderLayout());

		JPanel workflowPanel = new JPanel();
		workflowPanel.setLayout(new BorderLayout());

		model = new DefaultTableModel();
		model.addColumn("Filter");
		model.addColumn("Contents");

		table = new JTable(model);

		JScrollPane scrollPane = new JScrollPane(table);
		table.setFillsViewportHeight(true);
		table.setPreferredScrollableViewportSize(new java.awt.Dimension(50, 50));
		
		JButton addButton = new JButton("Add Filter to Workflow");
		JButton removeButton = new JButton("Remove Filter");

		addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				activeFilter.processFilterWindowComponent();
				gatts.pipeline.add(activeFilter);

				model.addRow(new String[] { activeFilter.getName(),
						activeFilter.toJSONString() });

				// /reset the activeFilter..
				selectedItemChanged(activeFilter.getName());
				gd.validate();
				gd.repaint();
			}
		});
		
		removeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int[] selectedRows = table.getSelectedRows();
				
				if(selectedRows == null)
					return;
				
				java.util.Arrays.sort(selectedRows);
				
				for(int i = selectedRows.length-1; i >= 0; --i) {
					model.removeRow(selectedRows[i]);	
					gatts.pipeline.remove(selectedRows[i]);
				}
			}
		});

		workflowPanel.add(filterPanel, BorderLayout.NORTH);
		workflowPanel.add(scrollPane, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel();
		buttonPanel.add(addButton);
		buttonPanel.add(removeButton);

		workflowPanel.add(buttonPanel, BorderLayout.SOUTH);
		workflow.add(workflowPanel, BorderLayout.CENTER);

		return workflow;
	}
	
	/**
	 * Clean the work flow entries and pipeline.
	 */
	public void cleanWorkflowTable() {
		int numRows = table.getRowCount();
		for (int i = numRows-1; i >= 0; --i) {
			model.removeRow(i);
		}
		gatts.pipeline.clear();
	}

	/**
	 * Creates footer panel for the GUI
	 * @return Footer awt panel
	 */
	public Panel createFooter() {
		
		Panel footer = new Panel();

		JPanel footerPanel = new JPanel();
		footerPanel.setLayout(new GridLayout(2, 2, 0, 0));

		final JCheckBox virtualStack = new JCheckBox("Use Virtual Stack",
				useVirtualStack);
		
		final JTextField virtualTextField = new JTextField("");
		virtualTextField.setText("");
		virtualTextField.setEditable(false);
		
		JCheckBox sis = new JCheckBox("Show Intermediate Steps", gatts.intermediateSteps);
		sis.setEnabled(false);

		virtualStack.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				useVirtualStack = !useVirtualStack;
				virtualDirectory = null;
				
				if(useVirtualStack) {
					JFileChooser chooser = new JFileChooser(); 
				    
					chooser.setCurrentDirectory(new java.io.File("."));
				    chooser.setDialogTitle("Choose Output Directory");
				    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				    
				    chooser.setAcceptAllFileFilterUsed(false);
				    
				    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) { 
				      virtualDirectory = chooser.getSelectedFile();
				      virtualTextField.setText(virtualDirectory.toString());
				    }
				    else {
				    	//System.out.println("No Selection ");
				    	/// force a false selection, this will not come back here
				    	/// since useVirtualStack show be false during next callback..
				    	virtualStack.setSelected(false);
				    	virtualTextField.setText("");
				    }
				}
			}
		});
		
		
		sis.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				gatts.intermediateSteps = !gatts.intermediateSteps;
			}
		});
				
		footerPanel.add(virtualStack);
		footerPanel.add(virtualTextField);
		footerPanel.add(sis);
		footer.add(footerPanel);
		
		return footer;
	}
	
	/**
	 * Creates devices panel for the GUI
	 * @return Devices awt panel
	 */
	public Panel createFooterDevices() {
		Panel footerDevices = new Panel();
		footerDevices.setLayout(new BorderLayout());
		
		Panel footerDevicesPanel = new Panel();
		footerDevicesPanel.setLayout(new BorderLayout());
		
		footerDevicesPanel.add(devicePanel, BorderLayout.NORTH);
		footerDevices.add(devicePanel, BorderLayout.NORTH);

		return footerDevices;
	}
	
	/**
	 * Event method for preview image scrolling
	 */
	public synchronized void adjustmentValueChanged(AdjustmentEvent e) { 
		int z = sliceSelector.getValue(); 
        previewResult.setSlice(z); 
        imageCanvas.setImageUpdated(); 
        imageCanvas.repaint(); 
    } 
	
	/**
	 * Creating temporary images for previewing
	 */
	private void updatePreviewInput() {
		String sSliceLabel = "";

		ImageStack aux = activeInput.getStack();
		ImageStack stack = null;
				
        if (activeInput.getWidth()>400 && activeInput.getHeight()>400) {
        	stack = aux.crop(activeInput.getWidth()/2-200,activeInput.getHeight()/2-200,0,400,400,10);
        } else if(activeInput.getWidth()>400 && activeInput.getHeight()<=400) {
        	stack = aux.crop(activeInput.getWidth()/2-200,0,0,400,activeInput.getHeight(),10);
        } else if(activeInput.getHeight()>400 && activeInput.getWidth()<=400) {
        	stack = aux.crop(0,activeInput.getHeight()/2-200,0, activeInput.getWidth(),400,10);
        } else {
        	stack = aux.crop(0,0,0,activeInput.getWidth(),activeInput.getHeight(),10);
        }
        
//        ColorModel cm = activeInput.createLut().getColorModel();
//        ImageStack substack = new ImageStack(stack.getWidth(),
//        									 stack.getHeight(),
//                                             cm);   
//        for(int n=1; n<=10; ++n) {
//        	activeInput.setSlice(n);
//        	
//            sSliceLabel = stack.getSliceLabel(n);
//            if (sSliceLabel !=null && sSliceLabel.length() < 1) {
//            	sSliceLabel = "slice_"+String.valueOf(n);
//            }
//
//            substack.addSlice(sSliceLabel,  activeInput.getProcessor().duplicate());
//        }
//        activeInput.setSlice(1);
        
        String sStackName = "PreviewInput";
        
        if (previewInput==null) 
        	previewInput = new ImagePlus(sStackName, stack);
        else
        	previewInput.setStack(stack);
        previewInput.setCalibration(activeInput.getCalibration());
        
		if (previewResult==null) {
			previewResult = previewInput.duplicate();
		    ImageProcessor previewResultProc = previewInput.getProcessor();
		    previewResult.setProcessor(previewResultProc);
		    previewResult.setTitle("PreviewResult");
		} else {
			previewResult.setStack(stack);
		}
	}
	
	/**
	 * Creates right top panel for the GUI
	 * @return Right top awt panel
	 */
	public Panel createPreviewerPanel() {
		Panel preview = new Panel();

		previewJPanel = new JPanel();
		previewJPanel.setLayout(new BoxLayout(previewJPanel, BoxLayout.Y_AXIS));

		//Create previewer
		
		activeInput = WindowManager.getImage((String) inputBox.getSelectedItem());

		updatePreviewInput();
		
        
		// The stack:
        imageCanvas = new ImageCanvas(previewResult);
        imageCanvas.setVisible(true);
        previewJPanel.add(imageCanvas, BorderLayout.CENTER);

        // The scrollbar
        sliceSelector = new Scrollbar();
        sliceSelector.setMinimum(1); 
        sliceSelector.setMaximum(20);
        sliceSelector.setOrientation(Scrollbar.HORIZONTAL); 
        sliceSelector.setVisible(true);
        sliceSelector.addAdjustmentListener(this);
        previewJPanel.add(sliceSelector, BorderLayout.SOUTH); 
		
		previewb = new JButton("Preview");
		previewb.setAlignmentX(Component.CENTER_ALIGNMENT);
		previewJPanel.add(previewb, BorderLayout.CENTER);
        
		previewb.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				
				if (gatts.pipeline.size() == 0) {
					JOptionPane.showMessageDialog(null,
		    				"Please add at least one filter to the pipeline! ", 
		    				"Pipeline Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				
	            filter.run(previewInput.getProcessor(), e);
	            imageCanvas.setImageUpdated(); 
	            imageCanvas.repaint(); 
			}
		});
        
		preview.add(previewJPanel);

		return preview;
	}
	
	/**
	 * Creates bottom panel of main window. For now this is empty and it is created 
	 * just to adjust the position of OK and CANCEL buttons
	 */
	public Panel createBottomPanel() {
		Panel bottom = new Panel();
		bottom.setLayout(new BorderLayout());
		Panel bottomJPanel = new Panel();
		bottom.add(bottomJPanel);
		return bottom;
	}
	
	/**!
	 * Run a macro command from string
	 * @param macro - parsed from string
	 * @return whether the operation was successful
	 */
	public boolean runMacro(String macro) {
		return fromString(macro);
	}
	
	/**
	 * Run the main interface
	 * @return whether the operation is to be accepted or canceled.
	 */
	public boolean runInterface() {
			
        gatts.inputDeviceLength = maxNumDevices;
		
		gd.setLayout(new BoxLayout(gd, BoxLayout.Y_AXIS));
		
		gd.add(createTopPanel());
		gd.add(createBottomPanel());
				
		selectedItemChangedInput();

		// show the dialog
		gd.showDialog();

		if (gd.wasCanceled()) {
			return false;
		}
				
		/// only ok was clicked, so take the current state
		/// of the active filter and add it to the list..
		if (gatts.pipeline.size() == 0) {
			JOptionPane.showMessageDialog(null,
    				"Please add at least one filter to the pipeline! ", 
    				"Pipeline Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		activeInput = WindowManager.getImage((String) inputBox.getSelectedItem());
		
		for (int i=0;i<devices.length;i++) {
			gatts.maxSliceCount.set(i, (int) devicesSpList.get(i).getValue());
		}
		
	    /**
	     * If requested, record the operation
	    */
	
		if(Recorder.record) {
			Recorder.recordOption("Options", toJSONString());
		}
			
		// Creating log file for monitoring
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
			
		String devices_string = "";
		for (int i=0;i<devices.length;i++) {
			devices_string = devices_string+devices[i].getName()+"\n";
		}
			
		monitor.setKeyValue("date", dateFormat.format(date));
		monitor.setKeyValue("devices", devices_string);
		monitor.setKeyValue("f3d.parameters", toString());
		return true;
	}
	
	/**
	 * run the macro version of the library (no interface).
	 * @return whether the operation was successful
	 */
	public boolean run() 
	{
		String macroOptions = Macro.getOptions();
		
		if(macroOptions != null) {
			//System.out.println("Running macro...");
			
			// Creating log file for monitoring
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			
			String devices_string = "";
			for (int i=0;i<devices.length;i++) {
				devices_string = devices_string+devices[i].getName()+"\n";
			}
			
			monitor.setKeyValue("date", dateFormat.format(date));
			monitor.setKeyValue("devices", devices_string);
			monitor.setKeyValue("f3d.parameters", macroOptions);
			
			return runMacro(macroOptions);
		}
		
		return runInterface();
	}
	
	/**!
	 * Construct attributes from user input.
	 * @return attributes to be used by F3D
	 */
	FilteringAttributes createAttributes() {
		FilteringAttributes atts = new FilteringAttributes();

		atts.intermediateSteps = gatts.intermediateSteps;
		atts.pipeline = gatts.pipeline;
		atts.chooseConstantDevices = gatts.chooseConstantDevices;
		atts.inputDeviceLength = gatts.inputDeviceLength;
		atts.maxSliceCount = gatts.maxSliceCount;		
		atts.overlap = gatts.overlap;
		return atts;
	}

	/**
	 * Gets the selected index
	 * @param choice
	 * @return JOCL Filter mapping to chosen index.
	 */
	protected JOCLFilter getChosenIndex(String choice) {

		if (filterList.containsKey(choice)) {
			return filterList.get(choice).newInstance();
		}

		return null; // / convert this to None..
	}

	/**!
	 * detection change in selection therefore the user interface
	 * needs to be updated
	 * @param selectedItem
	 */
	private void selectedItemChanged(String selectedItem) {
		activeFilter = getChosenIndex(selectedItem);
		filterPanel.removeAll();
		filterPanel.add(activeFilter.getFilterWindowComponent());
		gd.validate();
		gd.repaint();
	}
	
	/**!
	 * Create/Update the device window component based on selection.
	 */
	private void selectedItemChangedInput() { 
		devicePanel.removeAll();
		chosenDevices.clear();
		chosenDevicesIdxs.clear();
		devicePanel.add(getDeviceWindowComponent());
		gd.validate();
		gd.repaint();
	}
	
	/**!
	 * Update user interface based on selection.
	 * @param state
	 */
	public void enableZorderChanged(boolean state) {
		int globalMemSize = 0;
		int maxOverlap = 0;
		int maxSliceCount = 0;
		
		gatts.overlap.clear();
		gatts.maxSliceCount.clear();
		
		int[] dims = activeInput.getDimensions();

        gatts.width = dims[0];
        gatts.height = dims[1];
        gatts.channels = dims[2];
        gatts.slices = dims[3];
		
        for(int i = 0; i < gatts.pipeline.size(); ++i)
        {
            JOCLFilter filter = gatts.pipeline.get(i);
            maxOverlap = Math.max(maxOverlap, filter.getInfo().overlapZ);
        }
        
        for (int i=0;i<devices.length;i++) {
        	globalMemSize = (int) Math.min(devices[i].getMaxMemAllocSize()*.5, Integer.MAX_VALUE >> 1);

        	if(devices[i].getType().equals("CPU")) {
        		globalMemSize = (int) Math.min(globalMemSize, 10*1024*1024); /// process 100MB at a time
        	}

        	maxSliceCount = (int)(((double)globalMemSize / ((double)gatts.width*gatts.height)));
        
	        maxSliceCount -= maxOverlap; 
	        
	        if(maxSliceCount <= 0){
	            //System.out.println("Image + StructureElement will not fit on GPU memory");
	            //return false;
	        }            
	    
	        /// if the maximum slices are greater than available slices then
	        /// clamp to slices
	        if(maxSliceCount > gatts.slices) {
	            maxSliceCount = gatts.slices;
	            //atts.maxSliceCount = -1;
	            maxOverlap = 0;
	        }
	        
	        gatts.overlap.add(maxOverlap);
			gatts.maxSliceCount.add(maxSliceCount);
        }
	}
	
	/**!
	 * Create dynamic interface to list of available OpenCL 
	 * accelerators.
	 * @return user interface component.
	 */
	public Component getDeviceWindowComponent() {
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(2+devices.length, 2, 0, 0));
		
		PluginDialogButtonGroup devicesCbGroup = new PluginDialogButtonGroup();
			
		final ArrayList<JCheckBox> devicesCbList = new ArrayList<JCheckBox>();
		devicesSpList = new ArrayList<JSpinner>();
		
		JLabel devicesLabel = new JLabel("Devices Options");
		JLabel numdevicesLabel = new JLabel("Total number of devices: " + maxNumDevices);

		panel.add(devicesLabel);
		panel.add(numdevicesLabel);
		
		enableZorderChanged(false);
		
		for (int i=0;i<devices.length;i++) {
			
			final JCheckBox cb = new JCheckBox("Device " + i + " (" + devices[i].getName() + ")",false);
			
			final JSpinner js = new JSpinner(new SpinnerNumberModel((int)gatts.maxSliceCount.get(i),1,(int)gatts.maxSliceCount.get(i),1));
			js.setEnabled(false);
			
			js.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					int index = devicesSpList.indexOf(js);
					gatts.maxSliceCount.set(index, (Integer) ((JSpinner) e.getSource()).getValue());
				}
			});
			
			cb.addItemListener(new ItemListener() {
				@Override
				public void itemStateChanged(ItemEvent e) {
					js.setEnabled(e.getStateChange()==ItemEvent.SELECTED);
					if (e.getStateChange()==ItemEvent.SELECTED) {
						chosenDevices.add(devices[devicesCbList.indexOf(cb)]);
						chosenDevicesIdxs.add(devicesCbList.indexOf(cb));
					} else {
						chosenDevices.remove(devices[devicesCbList.indexOf(cb)]);
						chosenDevicesIdxs.remove((Integer)devicesCbList.indexOf(cb));
					}
				}
			});
			
			devicesCbList.add(cb);
			devicesSpList.add(js);
			
			panel.add(cb);
			panel.add(js);
		}

		devicesCbList.get(0).setSelected(true);
		
		devicesCbGroup.addAll(devicesCbList);
		
		return panel;
	}
	
	/**!
	 * Serialize to JSON string.	
	 * @return serialized string
	 */
	public String toJSONString() {
		int i;
		String result = "";
		
		String filters = "[";
		
		for(i = 0; i < gatts.pipeline.size(); ++i) {
			filters += gatts.pipeline.get(i).toJSONString();
			
			if(i != gatts.pipeline.size()-1)
				filters += ",";
		}
		filters += "]";
		
		String devices = "[";
		
		for(i=0;i<chosenDevices.size();i++) {
			devices += "{ "
					+ "Device: " + chosenDevicesIdxs.get(i) + " , "
					+ "MaxNumSlices: " + gatts.maxSliceCount.get(i)
					+ " } ";
		}
		devices += "]";
		
		String virtualPath = virtualDirectory != null ?  virtualDirectory.getAbsolutePath() : "empty" ;
		
		result = "{"
				+ "input : " + activeInput.getTitle() + " , "
				+ "intermediateSteps : "+ gatts.intermediateSteps + " , "
				//+ "chooseConstantDevices : " + gatts.chooseConstantDevices + " , "
				//+ "inputDevices : " + gatts.inputDeviceLength + " , "
				+ "devices : " + devices + " , "
				+ "useVirtualStack : " + useVirtualStack + " , "
				+ "virtualDirectory : " + virtualPath + " , "
				+ "filters : " + filters.replace("\"","")
				+ "}";
		return result;
	}
	
	/**!
	 * Deserialize from JSON string
	 * @param str - input string
	 * @return whether deserialization was successful.
	 */
	public boolean fromString(String str) {
		int i;
		boolean result = true;
		JSONParser parser = new JSONParser();
		
		String key = "options=";
		
		if(!str.startsWith(key))
			return false;
		
		String command = str.substring(key.length());
		
		command = command.trim();
		
		if(command.startsWith("[") && command.endsWith("]")) {
			command = command.substring(1, command.length()-1);
		}
		
		///the rest should be JSON compliant 
		
		// Remove all white spaces from command
		command = command.replaceAll("\\s", "");
		
		// Treat substrings inside []
		String replaced = null;
		while (true) {
			replaced = command.replaceAll("(\\w+)[\\:](\\[.*\\])","\"$1\" : $2");
			if (replaced.equals(command)) {
                break;
            }
            command = replaced;
        }
		
		// Insert double quotes as needed for JSON
		command = command.replaceAll("(\\w+)[\\:]((\\/*\\w+(\\-|\\.|\\/))*\\w+)", "\"$1\" : \"$2\"");
		
		try {
			 
			Object obj = parser.parse(command);
	 
			JSONObject jsonObject = (JSONObject) obj;
			String imageName = (String) jsonObject.get("input");
			activeInput = WindowManager.getImage(imageName); //gatts.inputImage;
			gatts.intermediateSteps = Boolean.valueOf((String)jsonObject.get("intermediateSteps"));
			useVirtualStack = Boolean.valueOf((String)jsonObject.get("useVirtualStack"));
			if (useVirtualStack) {
				if (jsonObject.get("virtualDirectory")==null) {
					System.out.println("virtualDirectory is empty!");
					return false;
				} else {
					virtualDirectory = new File((String)jsonObject.get("virtualDirectory"));
					if (!virtualDirectory.exists() || virtualDirectory.getAbsolutePath()=="empty") {
						System.out.println("virtualDirectory does not exist!");
						return false;
					} else {

					}
				}
			}
					
			JSONArray filterArray = (JSONArray) jsonObject.get("filters");
			
			for (i=0;i<filterArray.size();i++) {
			
				JSONObject jsonFilterObject = (JSONObject) filterArray.get(i);
				String filterName = (String) jsonFilterObject.get("Name");
				activeFilter = getChosenIndex(filterName);
				activeFilter.fromJSONString(jsonFilterObject.toString());
				activeFilter.processFilterWindowComponent();
				gatts.pipeline.add(activeFilter);
			}
				
			enableZorderChanged(false);
					
			JSONArray deviceArray = (JSONArray) jsonObject.get("devices");
			int deviceIndex;
			int MaxNumSlices;
			for (i=0;i<deviceArray.size();i++) {
				JSONObject jsonDeviceObject = (JSONObject) deviceArray.get(i);
				deviceIndex = Integer.valueOf((String)jsonDeviceObject.get("Device"));
				if (deviceIndex>=devices.length) {
					System.out.println("Unavailable number of device!");
					return false;
				} else 
					chosenDevicesIdxs.add(deviceIndex);
				MaxNumSlices = Integer.valueOf((String)jsonDeviceObject.get("MaxNumSlices"));
				if (MaxNumSlices > gatts.maxSliceCount.get(i)+gatts.overlap.get(i)) {
					System.out.println("Incorrect number of slices!");
					return false;
				} else 
					gatts.maxSliceCount.set(i,MaxNumSlices);
				chosenDevices.add(devices[chosenDevicesIdxs.get(i)]);
			}
			
			 
		} catch (ParseException e) {
			e.printStackTrace();
		}
				
		return result;
	}

	
}
