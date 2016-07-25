package F3DImageProcessing_JOCL_;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.json.simple.JSONObject;

/**
 * Monitoring machine and plugin information for debugging using JSON string
 * @author tperciano
 *
 */
public class F3DMonitor {
	
	String logfilename;
	String plugins_path;
	//FileWriter fw;
	JSONObject JSONlogString = new JSONObject();
	
	@SuppressWarnings("unchecked")
	F3DMonitor() {
		
		addSystemProps();
		
		JSONlogString.put("date", "");
		JSONlogString.put("devices", "");
		JSONlogString.put("f3d.parameters", "");
		JSONlogString.put("drivers.exception", "");
		JSONlogString.put("deps.exception", "");
		JSONlogString.put("bilateral.comperror", "");
		JSONlogString.put("bilateral.allocerror", "");
		JSONlogString.put("mask.comperror", "");
		JSONlogString.put("mask.allocerror", "");
		JSONlogString.put("median.comperror", "");
		JSONlogString.put("median.allocerror", "");
		JSONlogString.put("dilation.comperror", "");
		JSONlogString.put("dilation.allocerror", "");
		JSONlogString.put("erosion.comperror", "");
		JSONlogString.put("erosion.allocerror", "");
		JSONlogString.put("opening.allocerror", "");
		JSONlogString.put("comp.time", "");
		
		
	}
	
	/**!
	 * Set key value in to JSON log
	 * @param key
	 * @param value
	 */
	@SuppressWarnings("unchecked")
	void setKeyValue(String key, String value) {
		JSONlogString.put(key, value);
	}
	
	/**!
	 * Save system properties.
	 */
	@SuppressWarnings("unchecked")
	private void addSystemProps() {
				
		JSONlogString.put("os.name", System.getProperty("os.name"));
		JSONlogString.put("os.arch", System.getProperty("os.arch"));
		JSONlogString.put("java.version", System.getProperty("java.version"));
		JSONlogString.put("user.dir", System.getProperty("user.dir"));
		JSONlogString.put("os.proc", Integer.toString(Runtime.getRuntime().availableProcessors()));		
		
	}
	/**!
	 * Write to Automated Log File.
	 */
	void writeToLog() {
		try {
			/**!
			 * Create F3D log file.
			 */
			this.plugins_path = ((String) System.getProperties().get("plugins.dir"));
			new File(plugins_path+"/F3D/").mkdir();
			this.logfilename = "F3D_log.txt";
			FileWriter fw = new FileWriter(plugins_path+"/F3D/"+logfilename,true);
			fw.write(this.JSONlogString.toString());
			fw.write("\n");
			fw.close();
		} catch (IOException e) {

		}	
	}
	
	/**!
	 * Write to provided Log file
	 * @param fw - input log file.
	 */
	void writeToLog(FileWriter fw) {
		try {
			/**!
			 * Create F3D log file.
			 */
			this.plugins_path = ((String) System.getProperties().get("plugins.dir"));
			new File(plugins_path+"/F3D/").mkdir();
			this.logfilename = "F3D_log.txt";
			fw.write(this.JSONlogString.toString());
			fw.write("\n");
		} catch (IOException e) {

		}	
	}
	
}
