 /*
  * This script test different sizes of images and record timing
  * 1) Creates a mask
  * 2) Creates a big image
  * 3) output several files: n  image files and 1 timing file
  */

macro "Compare filters" {

 setBatchMode(true);
 
 debugingThisCode = false; //<<<<<<<<<=CHANGE HERE !!!!
 path = "/home/users/tperciano/workspace/f3d-maven/tests/";
 run("Close All");

 
 /*
  * Create mask
  */
 width = 3; 
 height = 3; depth = 3;
 	name = "w_"+width+"h_"+ height+"d_"+depth;
	newImage("Strel"+name, "8-bit black", width, height, depth);
	for(n=1;n<=nSlices;n++){
		setSlice(n);
		for (i=0;i<width;i++)
		for (j=0;j<height;j++)
		if (j<50)
			setPixel(i,j,255);
	}		
 structElemWindow = getTitle;	
  	
 /*
  *  Creates images with different sizes and measure MM computing time
  */ 
 if (debugingThisCode)
 	n=1;
 else
	n = 7;
 depthArray = newArray(n); //4,15,30,60,120,240,480 image in MBs;
 
 for (j=0;j<lengthOf(depthArray);j++)
 	depthArray[j]=j*13+1;
 	//print((depthArray[j]));

 //depthArray[0]=1;
 /*for (j=1;j<lengthOf(depthArray);j++) {
	num = round(depthArray[j-1]*1.595);
	if (num==depthArray[j-1])
		num++;
	depthArray[j]=num;
	print(depthArray[j]);
 }
 if (!debugingThisCode) 
	depthArray[20]=30185;*/
 
 //width = 1800; 
 //height = 1950; //dim = Hrishi's data
 
 width = 1000;
 height = 1000;

 nrepeats=1; //to check how much time is varyings

 maxNSlices = 305;
 NSlices = 305;

 FileName = fReturnFileName();
 for(bothRuns=1;bothRuns>=0;bothRuns--){
 	 
	 if(bothRuns==0)
	 	newFileName = "f"+FileName+"_s"; //serial jarek 
	 if(bothRuns==1)
	 	newFileName = "f"+FileName+"_p"; //parallel f3d
	 //if(bothRuns==2)
	 //	newFileName = "f"+FileName+"_p_vs";
	 	
	 //fileTime = File.open(path+newFileName+".txt");
	 
	 for(item=0;item<lengthOf(depthArray);item++)
	 {		
	 	print("Depth: "+depthArray[item]);
		depth = depthArray[item];

		name="InputImage"+depth;
		createTestImage(name,width,height,depth);

		/*if (bothRuns==0 || bothRuns==1)
	 		run("Image Sequence...", "open=/home/tperciano/20p8lb/20p8lb_0000.tif number="+depth+" sort");
		else
			run("Image Sequence...", "open=/home/tperciano/20p8lb/20p8lb_0000.tif number="+depth+" sort use");*/
			
		rename(name);
		origTitle=getTitle;
		origI=getImageID;
		repeat=0;
	 	while(repeat<nrepeats)
	 	{
		 	fileTime = File.open(path+newFileName+"_depth"+depthArray[item]+"_repeat"+repeat+".txt");
			
			if(bothRuns==0){
				t0 = getTime;
				run("Morphological Dilate 3D");		
			}
			if(bothRuns==1){
				if (depth <= maxNSlices)
					NSlices = depth;
				else
					NSlices = maxNSlices;
				t0 = getTime;
				//t0 = getTime;
				//run("QuantCT Filtering (JOCL)", "input="+orig+" algorithm=MMero3D stream=-1 stream=-1 stream=-1 spatial=3 range=50 show windows="+structElemWindow);
				//run("QuantCT Filtering (JOCL)", "input=bat-cochlea-renderings.tif algorithm=MMdil3D spatial=3 range=50 windows=Strelw_3h_3d_3 l=10");
				//run("QuantCT Filtering (JOCL)", "input="+origTitle+" algorithm=MMdil3D spatial=3 range=50 windows="+structElemWindow+" l=10 opencl=4");
				run("F3D Image Processing (JOCL)", "options=[{input : "+origTitle+" , intermediateSteps : false , enableZorder : false , devices : [{ Device: 0 , MaxNumSlices: "+NSlices+" } { Device: 1 , MaxNumSlices: "+NSlices+" } ] , useVirtualStack : false , virtualDirectory : empty , filters : [{ Name : MMFilterDil , Mask : [{maskImage : "+structElemWindow+"}] }]}]");     //No virtual stack
				//run("QuantCT Filtering (JOCL)", "options=[{input : "+origTitle+" , intermediateSteps : false , enableZorder : false , devices : [{ Device: 0 , MaxNumSlices: "+NSlices+" } { Device: 1 , MaxNumSlices: "+NSlices+" } { Device: 2 , MaxNumSlices: "+NSlices+" } { Device: 3 , MaxNumSlices: "+NSlices+" } ] , useVirtualStack : true , virtualDirectory : /home/tperciano/Performance/debug/temp , filters : [{ Name : MMFilterDil , Mask : [{maskImage : "+structElemWindow+"}] }]}]");
			}
			/*if(bothRuns==2){   // Virtual Stack
				if (depth <= maxNSlices)
					NSlices = depth;
				else
					NSlices = maxNSlices;
				t0 = getTime;
				run("QuantCT Filtering (JOCL)", "options=[{input : "+origTitle+" , intermediateSteps : false , enableZorder : false , devices : [{ Device: 0 , MaxNumSlices: "+NSlices+" } { Device: 1 , MaxNumSlices: "+NSlices+" } { Device: 2 , MaxNumSlices: "+NSlices+" } { Device: 3 , MaxNumSlices: "+NSlices+" } ] , useVirtualStack : true , virtualDirectory : /home/tperciano/20p8lb , filters : [{ Name : MMFilterDil , Mask : [{maskImage : "+structElemWindow+"}] }]}]");
			}*/
			
			procI = getImageID;
			while(procI == origI){
				print("Race condition");
				wait(500);
			}

			print(procI);
			print(origI);
			tf = getTime - t0;
			print(fileTime, "depth="+depth+" timeProc="+tf +"\n");
			File.close(fileTime);

			selectImage(procI);
			close; //close original
			//print(path + newFileName + depth +".tif");
			saveAs("Tiff", path + newFileName + depth +".tif");
						
			//wait(500); //avoid closing before saving! Stupid!
			repeat++;
		}//end while
		selectImage(origI);
		close; //close processed 
		
		run("Collect Garbage");
	 }// end for item
	 //File.close(fileTime);
 }//end for bothRuns

 
/*
 * Compare if the MM operators from serial and sequential give the same result
 */

 //Read images
 imageFileName = fReturnFileName(); 
 imageFileName = "f"+imageFileName; // f necessary because of parser problems with fiji in andromeda
 for(item=0; item<lengthOf(depthArray); item++){
 	depth = depthArray[item];
 	open( path+imageFileName +"_s"+ depth +".tif" );
 	serial_result = getImageID;
 	
 	open( path+imageFileName +"_p"+ depth +".tif" );
 	parallel_result = getImageID;
 	
 	imageCalculator("Subtract create 32-bit stack", serial_result, parallel_result);
	cumValue = 0;
	for (i=0;i<nSlices;i++){
		setSlice(i+1);
		run("Set Measurements...", "  integrated redirect=None decimal=4");
		run("Measure");
		value=getResult("IntDen",nResults-1);
		cumValue+=value;
	}
	print("Sum of difference for "+depth+ " depths = "+ cumValue);
	close; close; close;
	//run("Close"); //close results table
 }
 setBatchMode(false);
//end of macro
}

function createTestImage(name,width, height, depth){
	newImage(name, "8-bit white", width, height, depth);
	setColor("black"); 
	setFont("Arial", round(width/40), "bold"); //otherwise you cannot see the number
	for(n=1;n<=nSlices;n++){
		setSlice(n);
		x = random()*(width-80)+40;
		y = random()*(height-80)+40;
		drawString(d2s(n,0), x, y);
	}
}

function fReturnFileName(){
	getDateAndTime(year, month, dayOfWeek, dayOfMonth, hour, minute, second, msec);
	month++;
	newFileName = "f"+year + "_" + month+ "_" + dayOfMonth;
	//print(newFileName);
	return newFileName;
}	
