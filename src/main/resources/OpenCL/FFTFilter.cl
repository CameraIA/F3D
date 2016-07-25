bool isOutsideBounds(const int3 pos, const int3 sizes)
{
     if(pos.x < 0 || pos.y < 0 || pos.z < 0 ||
        pos.x >= sizes.x || pos.y >= sizes.y || pos.z >= sizes.z)
        return true;

    size_t index = pos.x + pos.y*sizes.x + pos.z*sizes.x*sizes.y;

    if(index >= sizes.x*sizes.y*sizes.z)
        return true;

    return false;
}
#define PI2 6.2832
/**!
 * One Dimensional FFT Filter : direction = 0 (X), 1 (Y), 2 (Z)
 */
void FFTFilterZ(global const float* inputBuffer, 
                global float* realBuffer, 
                global float* imagBuffer,  
                const int direction,   
                const int3 sizes)
{
    int N = sizes.z;
	int3 pos = { get_global_id(0), get_global_id(1), 0 };
    if(isOutsideBounds(pos, sizes)) return;
    
    ///TODO: move DFT to FFT.	
    for (int k=0 ; k < N ; ++k)
    {
        size_t kindex = pos.x + pos.y*sizes.x + k*sizes.x*sizes.y;
      
        realBuffer[kindex] = imagBuffer[kindex] = 0;
        
        for (int n=0 ; n < 10 ; ++n)  {
            size_t nindex = pos.x + pos.y*sizes.x + n*sizes.x*sizes.y;
         	
         	realBuffer[kindex] += inputBuffer[nindex] * cos(direction * n * k * PI2 / N);
            imagBuffer[kindex] -= inputBuffer[nindex] * sin(direction * n * k * PI2 / N);
        }
        
        if(direction == -1) {
           realBuffer[kindex] /= N;
           imagBuffer[kindex] /= N;
        }
        // Power at kth frequency bin
        //realBuffer[kindex] = realBuffer[kindex]*realBuffer[kindex] + imagBuffer[kindex]*imagBuffer[kindex];
    }           
}

void FFTFilterY(global const float* inputBuffer, 
                global float* realBuffer, 
                global float* imagBuffer,  
                const int direction,   
                const int3 sizes)
{
    int N = sizes.y;
	int3 pos = { get_global_id(0), 0, get_global_id(1) };
	if(isOutsideBounds(pos, sizes)) return;
   
    ///TODO: move DFT to FFT.     
    for (int k=0 ; k < N ; ++k)
    {
        size_t kindex = pos.x + k*sizes.x + pos.z*sizes.x*sizes.y;
      
        realBuffer[kindex] = imagBuffer[kindex] = 0;
        
        for (int n=0 ; n < 10 ; ++n)  {
            size_t nindex = pos.x + n*sizes.x + pos.z*sizes.x*sizes.y;
         	
         	realBuffer[kindex] += inputBuffer[nindex] * cos(direction * n * k * PI2 / N);
            imagBuffer[kindex] -= inputBuffer[nindex] * sin(direction * n * k * PI2 / N);
        }
        
        if(direction == -1) {
           realBuffer[kindex] /= N;
           imagBuffer[kindex] /= N;
        }
        
        // Power at kth frequency bin
        //realBuffer[kindex] = realBuffer[kindex]*realBuffer[kindex] + imagBuffer[kindex]*imagBuffer[kindex];
    }       
}

void FFTFilterX(global const float* inputBuffer, 
                global float* realBuffer, 
                global float* imagBuffer,
                const int direction,   
                const int3 sizes)
{
    int N = sizes.x;
    int3 pos = { 0, get_global_id(0), get_global_id(1)};
    if(isOutsideBounds(pos, sizes)) return;
    
    ///TODO: move DFT to FFT.
    for (int k=0 ; k < N ; ++k)
    {
        size_t kindex = k + pos.y*sizes.x + pos.z*sizes.x*sizes.y;
      
        realBuffer[kindex] = imagBuffer[kindex] = 0;
        
        for (int n=0 ; n < 10 /* N */ ; ++n)  {
            size_t nindex = n + pos.y*sizes.x + pos.z*sizes.x*sizes.y;
         	realBuffer[kindex] += inputBuffer[nindex] * cos(direction * n * k * PI2 / N);
            imagBuffer[kindex] -= inputBuffer[nindex] * sin(direction * n * k * PI2 / N);
        }
        
        if(direction == -1) {
           realBuffer[kindex] /= N;
           imagBuffer[kindex] /= N;
        }
        
        // Power at kth frequency bin
        //realBuffer[kindex] = realBuffer[kindex]*realBuffer[kindex] + imagBuffer[kindex]*imagBuffer[kindex];
    }    
}

kernel void FFTFilter(global const float* inputBuffer, 
                      global float* realBuffer,
                      global float* imagBuffer,
                      int direction,
                      int imageWidth,
                      int imageHeight,
                      int imageDepth,   
                      int dimension)
{
    const int3 sizes = { imageWidth, imageHeight, imageDepth };
    
     
     if(dimension == 0) {
       FFTFilterX(inputBuffer, realBuffer, imagBuffer, direction, sizes);
     } else if(dimension == 1) {
       FFTFilterY(inputBuffer, realBuffer, imagBuffer, direction, sizes);
     } else {
       FFTFilterZ(inputBuffer, realBuffer, imagBuffer, direction, sizes);
     }
 }
