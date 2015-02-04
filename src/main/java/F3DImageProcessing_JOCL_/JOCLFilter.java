package F3DImageProcessing_JOCL_;

import java.awt.Component;

interface JOCLFilter
{
    String getName();
    int getDimensions();
    int overlapAmount();

    void setAttributes(CLAttributes clattr, FilteringAttributes atts, int index);
	
    boolean loadKernel();
    boolean runFilter();
    boolean releaseKernel();
    
    /// show and process panel..
	Component getFilterWindowComponent();
	void  processFilterWindowComponent();
	
	/// create new instance of the component..
	JOCLFilter newInstance();
	
	/// serialize and deserialize each component
	String toString();
	void fromString(String str);
}

