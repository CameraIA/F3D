package F3DImageProcessing_JOCL_;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Panel;
//import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
//import java.util.HashSet;
//import java.util.Vector;


import java.io.File;
import java.util.ArrayList;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
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

import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLDevice;

import ij.ImagePlus;
//import ij.gui.DialogListener;
//import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.frame.Recorder;

public class PluginDialog
{
	GenericDialog gd;
	FilteringAttributes gatts;
	int maxNumDevices;
	CLDevice[] devices;
	CLContext context;
	
	ArrayList<CLDevice> chosenDevices;
	ArrayList<Integer> chosenDevicesIdxs;

	
	//public String lastTitle = "";
	/// TODO: turn these variables private after testing.
	//public int inputDeviceLength = 1;
	//public boolean useVirtualStack = false;
	//public boolean chooseConstantDevices = false;
	//public File virtualDirectory = null;
	
	JPanel filterPanel;
	JPanel devicePanel;
	ImagePlus activeInput;
	JOCLFilter activeFilter;
	String[] types = null;
	JComboBox inputBox;
	
	DefaultTableModel model;
	JTable table;

	PluginDialog() {
		
		chosenDevices = new ArrayList<CLDevice>();
		chosenDevicesIdxs = new ArrayList<Integer>();
		
//		context = CLContext.create();
//        devices = context.getDevices();
//        maxNumDevices = devices.length;
		
		
		gd = new GenericDialog("F3D Parameters");
		filterPanel = new JPanel();
		devicePanel = new JPanel();
		
		
		gatts = new FilteringAttributes();
		activeFilter = null;
		activeInput = null;
		types = new String[gatts.filterList.keySet().size()];
		types = gatts.filterList.keySet().toArray(types);
		java.util.Arrays.sort(types);
		
	}

	public void setContext(CLContext contx) {
		context = contx;
		devices = context.getDevices();
		maxNumDevices = devices.length;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Panel createHeader() {
		Panel header = new Panel();

		JPanel headerJPanel = new JPanel();
		headerJPanel.setLayout(new GridLayout(2, 2, 0, 0));

		JLabel inputLabel = new JLabel("Input");

		inputBox = new JComboBox(
				FilteringAttributes.getImageTitles(false));

		gatts.inputImage = WindowManager.getImage((String) inputBox
				.getSelectedItem());

		inputBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
					gatts.inputImage = WindowManager.getImage((String) e.getItem());
					selectedItemChangedInput();
					cleanWorkflowTable();
				}
			}
		});

		// gd.addChoice("Algorithm", types, types[0]);
		JLabel algorithmLabel = new JLabel("Algorithm");
		JComboBox typeBox = new JComboBox(types);
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

		JButton addButton = new JButton("Add Filter to Workflow");
		JButton removeButton = new JButton("Remove Filter");

		addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				activeFilter.processFilterWindowComponent();
				gatts.filters.add(activeFilter);

				model.addRow(new String[] { activeFilter.getName(),
						activeFilter.toString() });

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
					gatts.filters.remove(selectedRows[i]);
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
	
	public void cleanWorkflowTable() {
		//System.out.println("Table cleaned!");
		int numRows = table.getRowCount();
		for (int i = numRows-1; i >= 0; --i) {
			model.removeRow(i);
		}
		gatts.filters.clear();
	}

	public Panel createFooter() {
		
		Panel footer = new Panel();

		JPanel footerPanel = new JPanel();
		//footerPanel.setLayout(new GridLayout(3, 2, 0, 0));
		footerPanel.setLayout(new GridLayout(2, 2, 0, 0));

//		final JCheckBox virtualStack = new JCheckBox("Use Virtual Stack",
//				useVirtualStack);
		final JCheckBox virtualStack = new JCheckBox("Use Virtual Stack",
				gatts.useVirtualStack);
		
		final JTextField virtualTextField = new JTextField("");
		virtualTextField.setText("");
		virtualTextField.setEditable(false);
		
		
		
//		JCheckBox cod = new JCheckBox("Use Constant OpenCL Devices",
//				chooseConstantDevices);
//		JCheckBox cod = new JCheckBox("Use Constant OpenCL Devices",
//				gatts.chooseConstantDevices);

//		JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, 1,
//				inputDeviceLength, 1));
		
//		JSpinner spinner = new JSpinner(new SpinnerNumberModel(gatts.inputDeviceLength, 1,
//				gatts.inputDeviceLength, 1));

		
		JCheckBox sis = new JCheckBox("Show Intermediate Steps", gatts.intermediateSteps);
		sis.setEnabled(false);

		JCheckBox ez = new JCheckBox("Enable Zorder", gatts.enableZorder);
		ez.setEnabled(false);

		virtualStack.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
//				useVirtualStack = !useVirtualStack;
//				virtualDirectory = null;
				gatts.useVirtualStack = !gatts.useVirtualStack;
				gatts.virtualDirectory = null;
				
//				if(useVirtualStack) {
				if(gatts.useVirtualStack) {
					JFileChooser chooser = new JFileChooser(); 
				    
					chooser.setCurrentDirectory(new java.io.File("."));
				    chooser.setDialogTitle("Choose Output Directory");
				    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				    
				    chooser.setAcceptAllFileFilterUsed(false);
				    
				    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) { 
				      //System.out.println("getCurrentDirectory(): " +  chooser.getCurrentDirectory());
				      //System.out.println("getSelectedFile() : " +  chooser.getSelectedFile());
//				      virtualDirectory = chooser.getSelectedFile();
//				      virtualTextField.setText(virtualDirectory.toString());
				      gatts.virtualDirectory = chooser.getSelectedFile();
				      virtualTextField.setText(gatts.virtualDirectory.toString());
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

		ez.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				gatts.enableZorder = !gatts.enableZorder;
				enableZorderChanged(gatts.enableZorder);
			}
		});
		
		footerPanel.add(virtualStack);
		footerPanel.add(virtualTextField);
		footerPanel.add(sis);
		footerPanel.add(ez);
		footer.add(footerPanel);
		
		return footer;
	}
	
	public Panel createFooterDevices() {
		Panel footerDevices = new Panel();
		footerDevices.setLayout(new BorderLayout());
		
		Panel footerDevicesPanel = new Panel();
		footerDevicesPanel.setLayout(new BorderLayout());
		
		footerDevicesPanel.add(devicePanel, BorderLayout.NORTH);
		footerDevices.add(devicePanel, BorderLayout.NORTH);

		return footerDevices;
	}
	
	public boolean runMacro(String macro) {
		return fromString(macro);
	}
	
	public boolean runInterface() {
		
		
        gatts.inputDeviceLength = maxNumDevices;
		
		gd.setLayout(new BoxLayout(gd, BoxLayout.Y_AXIS));
		gd.add(createHeader());
		gd.add(createWorkflow());
		gd.add(createFooter());
		selectedItemChanged(types[0]);
		gd.add(createFooterDevices());
		
		selectedItemChangedInput();
		
		
		gd.showDialog();

		if (gd.wasCanceled()) {
			//System.out.println("gd was canceled...");
			return false;
		}

		// / only ok was clicked, so take the current state
		// / of the active filter and add it to the list..
		if (gatts.filters.size() == 0) {
			activeFilter.processFilterWindowComponent();
			gatts.filters.add(activeFilter);
		}
		
		

		if(Recorder.record) {
			Recorder.recordOption("Options", toString());
		}
		return true;
	}
	
	public boolean run() 
	{
		String macroOptions = Macro.getOptions();
		
		if(macroOptions != null) {
			//System.out.println("Running macro...");
			return runMacro(macroOptions);
		}
		
		return runInterface();
	}

	FilteringAttributes createAttributes() {
		FilteringAttributes atts = new FilteringAttributes();

		atts.inputImage = gatts.inputImage;
		atts.intermediateSteps = gatts.intermediateSteps;
		atts.enableZorder = gatts.enableZorder;
		atts.useVirtualStack = gatts.useVirtualStack;
		atts.virtualDirectory = gatts.virtualDirectory;
		atts.filters = gatts.filters;
		atts.chooseConstantDevices = gatts.chooseConstantDevices;
		atts.inputDeviceLength = gatts.inputDeviceLength;
		atts.maxSliceCount = gatts.maxSliceCount;
		
		atts.overlap = gatts.overlap;
		
		return atts;
	}

	public JOCLFilter getChoiceIndex(String choice) {

		if (gatts.filterList.containsKey(choice)) {
			return gatts.filterList.get(choice).newInstance();
		}

		return null; // / convert this to None..
	}

	public void selectedItemChanged(String selectedItem) {
		activeFilter = getChoiceIndex(selectedItem);
		filterPanel.removeAll();
		filterPanel.add(activeFilter.getFilterWindowComponent());
		gd.validate();
		gd.repaint();
	}
	
	public void selectedItemChangedInput() { //TODO Change this for inputImage component (changes every time input image changes)
		activeInput = gatts.inputImage;
		devicePanel .removeAll();
		chosenDevices.clear();
		chosenDevicesIdxs.clear();
		devicePanel.add(getDeviceWindowComponent());
		gd.validate();
		gd.repaint();
	}
	
	public void enableZorderChanged(boolean state) {
		Zorder<Long> zorder = null;
		int globalMemSize = 0;
		int overlap = 0;
		int maxSliceCount = 0;
		int zOrderDepth = 0;
		gatts.overlap.clear();
		gatts.maxSliceCount.clear();
		gatts.zOrderDepth.clear();
		
		int[] dims = activeInput.getDimensions();

        gatts.width = dims[0];
        gatts.height = dims[1];
        gatts.channels = dims[2];
        gatts.slices = dims[3];
		
        for(int i = 0; i < gatts.filters.size(); ++i)
        {
            JOCLFilter filter = gatts.filters.get(i);
            overlap = Math.max(overlap, filter.overlapAmount());
        }

//        System.out.println("overlap: " + overlap);
        
        if(state) {
            zorder = new Zorder<Long>();
        }
        
        for (int i=0;i<devices.length;i++) {
//        	System.out.println("i: " +i);
        	if(!state) {
	
				globalMemSize = (int) Math.min(devices[i].getMaxMemAllocSize()*.8, Integer.MAX_VALUE >> 1);
								
				if(devices[i].getType().equals("CPU")) {
		            globalMemSize = (int) Math.min(globalMemSize, 10*1024*1024); /// process 100MB at a time
		        }
				
				maxSliceCount = (int)(((double)globalMemSize / ((double)gatts.width*gatts.height)));

						
	        }
	        else 
	        {
	            ///now allocate memory for Zorder memory on GPU..
	            //System.out.println("-->" + atts.width + " " + atts.height + " " + atts.slices);
	
	            gatts.zOrderWidth = zorder.zoRoundUpPowerOfTwo(gatts.width);
	            gatts.zOrderHeight = zorder.zoRoundUpPowerOfTwo(gatts.height);
	            
	            maxSliceCount = (int)(((double)globalMemSize / ((double)gatts.zOrderWidth*gatts.zOrderHeight)));
	            zOrderDepth = zorder.zoRoundUpPowerOfTwo(maxSliceCount);
	            
	        } 
	
	        maxSliceCount -= overlap; 
	        
	        if(maxSliceCount <= 0){
	            //System.out.println("Image + StructureElement will not fit on GPU memory");
	            //return false;
	        }            
	    
	        /// if the maximum slices are greater than available slices then
	        /// clamp to slices
	        if(maxSliceCount > gatts.slices) {
	            //System.out.println("Max slice count : " + atts.maxSliceCount + " " + atts.slices + " " + atts.overlap);
	            maxSliceCount = gatts.slices;
	            //atts.maxSliceCount = -1;
	            overlap = 0;
	        }
	        
	        if (state){
	        	zOrderDepth = zorder.zoRoundUpPowerOfTwo(maxSliceCount + overlap);
	        }
	        
//	        System.out.println("Overlap: " + overlap + ", maxSliceCount: " + maxSliceCount + ", zOrderDepth: " + zOrderDepth);
	        
	        gatts.overlap.add(overlap);
			gatts.maxSliceCount.add(maxSliceCount);
			gatts.zOrderDepth.add(zOrderDepth);
	        
        }
	}
	
	public Component getDeviceWindowComponent() {
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(2+devices.length, 2, 0, 0));
		
		PluginDialogButtonGroup devicesCbGroup = new PluginDialogButtonGroup();
			
		final ArrayList<JCheckBox> devicesCbList = new ArrayList<JCheckBox>();
		final ArrayList<JSpinner> devicesSpList = new ArrayList<JSpinner>();
		
		JLabel devicesLabel = new JLabel("Devices Options");
		JLabel numdevicesLabel = new JLabel("Total number of devices: " + maxNumDevices);

		panel.add(devicesLabel);
		panel.add(numdevicesLabel);
		
		enableZorderChanged(false);
		
		for (int i=0;i<devices.length;i++) {
			
			final JCheckBox cb = new JCheckBox("Device " + i + " (" + devices[i].getName() + ")",false);
			
//			System.out.println(gatts.maxSliceCount.get(i));
			
			final JSpinner js = new JSpinner(new SpinnerNumberModel((int)gatts.maxSliceCount.get(i),1,(int)gatts.maxSliceCount.get(i),1));
			js.setEnabled(false);
			
			js.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					int index = devicesSpList.indexOf(js);
					gatts.maxSliceCount.set(index, (Integer) ((JSpinner) e.getSource()).getValue());
					//System.out.println("MaxSliceCount: " + gatts.maxSliceCount);
				}
			});
			
			cb.addItemListener(new ItemListener() {
				@Override
				public void itemStateChanged(ItemEvent e) {
					js.setEnabled(e.getStateChange()==ItemEvent.SELECTED);
					if (e.getStateChange()==ItemEvent.SELECTED) {
						chosenDevices.add(devices[devicesCbList.indexOf(cb)]);
						chosenDevicesIdxs.add(devicesCbList.indexOf(cb));
						//System.out.println("Added device index: " + devicesCbList.indexOf(cb));
					} else {
						chosenDevices.remove(devices[devicesCbList.indexOf(cb)]);
						chosenDevicesIdxs.remove((Integer)devicesCbList.indexOf(cb));
						//System.out.println("Removed device index: " + devicesCbList.indexOf(cb));
					}
					//System.out.println("Temporary number of devices: " + chosenDevices.size());
				}
			});
			
			devicesCbList.add(cb);
			devicesSpList.add(js);
			
			panel.add(cb);
			panel.add(js);
		}

		devicesCbList.get(0).setSelected(true);
		
		devicesCbGroup.addAll(devicesCbList);
		
		
//		spinner.addChangeListener(new ChangeListener() {
//		@Override
//		public void stateChanged(ChangeEvent e) {
//	//		inputDeviceLength = (Integer) ((JSpinner) e.getSource())
//	//				.getValue();
//			gatts.inputDeviceLength = (Integer) ((JSpinner) e.getSource())
//					.getValue();
//		}
//		});
//	
//		cod.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent e) {
//	//			chooseConstantDevices = !chooseConstantDevices;
//				gatts.chooseConstantDevices = !gatts.chooseConstantDevices;
//			}
//		});
//
//		footerPanel.add(cod);
//		footerPanel.add(spinner);
//	
		
		return panel;
	}
	
		
	public String toString() {
		int i;
		String result = "";
		
		String filters = "[";
		
		for(i = 0; i < gatts.filters.size(); ++i) {
			filters += gatts.filters.get(i).toString();
			
			if(i != gatts.filters.size()-1)
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
		
		String virtualPath = gatts.virtualDirectory != null ?  gatts.virtualDirectory.getAbsolutePath() : "empty" ;
		
		result = "{"
				+ "input : " + gatts.inputImage.getTitle() + " , "
				+ "intermediateSteps : "+ gatts.intermediateSteps + " , "
				+ "enableZorder : " + gatts.enableZorder + " , "
				//+ "chooseConstantDevices : " + gatts.chooseConstantDevices + " , "
				//+ "inputDevices : " + gatts.inputDeviceLength + " , "
				+ "devices : " + devices + " , "
				+ "useVirtualStack : " + gatts.useVirtualStack + " , "
				+ "virtualDirectory : " + virtualPath + " , "
				+ "filters : " + filters.replace("\"","")
				+ "}";
		return result;
	}
	
	public boolean fromString(String str) {
		//boolean result = false;
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
//		System.out.println(command);
		
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
		
//		System.out.println(command);

		try {
			 
			Object obj = parser.parse(command);
	 
			JSONObject jsonObject = (JSONObject) obj;
			String imageName = (String) jsonObject.get("input");
			gatts.inputImage = WindowManager.getImage(imageName);
			activeInput = gatts.inputImage;
			//System.out.println("input: "+gatts.inputImage.getTitle());
			gatts.intermediateSteps = Boolean.valueOf((String)jsonObject.get("intermediateSteps"));
			//System.out.println("intermediateSteps: "+gatts.intermediateSteps);
			gatts.enableZorder = Boolean.valueOf((String)jsonObject.get("enableZorder"));
			//System.out.println("enableZorder: "+gatts.enableZorder);
			gatts.useVirtualStack = Boolean.valueOf((String)jsonObject.get("useVirtualStack"));
			//System.out.println("useVirtualStack: "+gatts.useVirtualStack);
			if (gatts.useVirtualStack) {
				if (jsonObject.get("virtualDirectory")==null) {
					System.out.println("virtualDirectory is empty!");
					return false;
				} else {
					gatts.virtualDirectory = new File((String)jsonObject.get("virtualDirectory"));
					if (!gatts.virtualDirectory.exists() || gatts.virtualDirectory.getAbsolutePath()=="empty") {
						System.out.println("virtualDirectory does not exist!");
						return false;
					} else {
						//System.out.println("virtualDirectory: "+gatts.virtualDirectory.getAbsolutePath());
					}
				}
			}
					
			JSONArray filterArray = (JSONArray) jsonObject.get("filters");
			//System.out.println("Filters: "+filterArray.toString());
			
			for (i=0;i<filterArray.size();i++) {
			
				JSONObject jsonFilterObject = (JSONObject) filterArray.get(i);
				String filterName = (String) jsonFilterObject.get("Name");
				//System.out.println("Filter name: "+filterName);
				activeFilter = getChoiceIndex(filterName);
				//System.out.println("Mask: "+jsonFilterObject.get("Mask"));
				activeFilter.fromString(jsonFilterObject.toString());
				activeFilter.processFilterWindowComponent();
				gatts.filters.add(activeFilter);
			}
	
//			gatts.inputDeviceLength = Integer.valueOf((String)jsonObject.get("inputDevices"));
//			System.out.println("inputDevices: "+gatts.inputDeviceLength);
//			gatts.chooseConstantDevices = Boolean.valueOf((String)jsonObject.get("chooseConstantDevices"));
//			System.out.println("chooseConstantDevices: "+gatts.chooseConstantDevices);
//			
//			if (gatts.chooseConstantDevices == true &&
//					gatts.inputDeviceLength < 0 && gatts.inputDeviceLength > maxNumDevices) {
//				System.out.println("Wrong number of devices!");
//				return false;
//			}
			
			enableZorderChanged(false);
					
			JSONArray deviceArray = (JSONArray) jsonObject.get("devices");
			//System.out.println("Devices: "+deviceArray.toString());
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
				//System.out.println("Device index: " + chosenDevicesIdxs.get(i));
				MaxNumSlices = Integer.valueOf((String)jsonDeviceObject.get("MaxNumSlices"));
				if (MaxNumSlices > gatts.maxSliceCount.get(i)+gatts.overlap.get(i)) {
					System.out.println("Incorrect number of slices!");
					return false;
				} else 
					gatts.maxSliceCount.set(i,MaxNumSlices);
				//System.out.println("MaxNumSlices: " + gatts.maxSliceCount.get(i));
				chosenDevices.add(devices[chosenDevicesIdxs.get(i)]);
			}
			
			 
		} catch (ParseException e) {
			e.printStackTrace();
		}
				
		return result;
	}
}
