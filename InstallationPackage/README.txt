README F3D Image Processing

Authors: Talita Perciano, Dani Ushizima and Hari Krishnan
September 2015

HOW TO INSTALL F3D

1 - Copy the file F3DImageProcessing_JOCL_-2.0.0-SNAPSHOT into the Fiji plugins folder. Use the file corresponding to your Java version: folders 1.7 or 1.8
2 - Copy all the files inside the jars folder to the Fiji jars folder
3 - Open Fiji/ImageJ and you should find the plugin under Menu -> Plugins -> Filtering

HOW TO USE F3D IN FIJI/IMAGEJ

1 - Open/import the image stack to be processed
2 - Go to Plugins -> Filtering -> F3D Image Processing (JOCL)
3 - User Interface:
	- Input: Choose the image to be processed
	- Algorithm: Choose the filter to be applied to the image and change the respective parameters if needed
	- Workflow Table: Use the button "Add Filter to Workflow" to include the filter in the workflow. You can include as many filters as you want. The filters will be applied sequentially.
	- "Use Virtual Stack" option: 
		- If using a non-virtual stack input image: choose this option if you want to save the results as a sequence of files in a specific local directory
		- If using an input image as virtual stack: option required if there is not enough memory available for the input image
4 - When you are ready, click "OK" to initiate the filtering process.


Thank you for using F3D Image Processing. To report a bug or to submit any question please send an e-mail to tperciano@lbl.gov 
using the subject "F3D Image Processing ALS". When reporting a bug, we kindly ask you to describe the issue in details along 
with the steps used that caused the bug. In doing so, we will be able to reproduce the bug and work on a solution.

