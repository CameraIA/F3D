/*
 * Masking of two volumes A and B, where A is the intensity image and B is the binary one, *with 1 for the preserving voxels and 0 for the removal voxels. Given voxels v1 and v2, the *result is v1*v2
 *OBS: notice it’s a pairwise matrix-item multiplication, not a matrix multiplication
 * transferring chucks of the matrices at a time, as opposed to calling the kernel for each voxel??
 * Created: 03/04/2013
 * Dani Ushizima
 */

//Core--------

kernel void mask3D(global const uchar* inputBufferIntensities, 
				   global const uchar* inputBufferBinary, 
				   global uchar* outputBuffer,
                   int imageWidth, int imageHeight, int imageDepth)
{

    size_t pos = get_global_id(0);
    size_t imageSize = imageWidth*imageHeight*imageDepth;
    
    if(pos > imageSize)
    	return;
    
    outputBuffer[pos] = (uchar) (inputBufferIntensities[pos] * ((float)inputBufferBinary[pos]/255.0)); 
    //outputBuffer[pos] = inputBufferIntensities[pos];  
    //outputBuffer[pos] = inputBufferBinary[pos]; 
}



