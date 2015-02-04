float dynamicKernel(float radius, int index)
{
    float x = (index + 1 - radius) / (radius * 2) / 0.2;
    return (float)exp(-0.5 * x * x);
}

kernel void makeKernel(float radius, global float* output, const int output_size)
{
    size_t i = get_global_id(0);

    if(i >= output_size) return;

    output[i] = dynamicKernel(radius,i);
}

kernel void normalizeKernel(float total, global float* output, const int output_size)
{
    size_t i = get_global_id(0);

    if(i >=  output_size) return;

     if (total <= 0.0)
        output[i] = 1.0f / output_size;
     else if (total != 1.0)
        output[i] /= total;
}

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

int getValue(global const uchar* buffer, const int3 pos, const int3 sizes)
{
     if(pos.x < 0 || pos.y < 0 || pos.z < 0 ||
        pos.x >= sizes.x || pos.y >= sizes.y || pos.z >= sizes.z)
        return -1;

    size_t index = pos.x + pos.y*sizes.x + pos.z*sizes.x*sizes.y;

    if(index >= sizes.x*sizes.y*sizes.z)
        return -1;

    return buffer[index];
}

void setValue(global uchar* buffer, const int3 pos, const int3 sizes, int value)
{
    size_t index = pos.x + pos.y*sizes.x + pos.z*sizes.x*sizes.y;
    buffer[index] = value;
}

void DaniBilateralFilter3D(const int3 pos, 
                           const int3 sizes, 
                           global const uchar* inputBuffer, 
                           global uchar* outputBuffer, 
                           global const float* spatialKernel, 
                           const int spatialSize,
                           global const float* rangeKernel, 
                           const int rangeSize)
{
    int v0 =  getValue(inputBuffer, pos, sizes);

    int sc = (int)spatialSize / 2;
    int rc = (int)rangeSize / 2;
    
    float v = 0;
    float total = 0;

    for (int n = 0; n < spatialSize; ++n)
    {
        for (int m = 0; m < spatialSize; ++m)
        {
            for (int k = 0; k < spatialSize; ++k)
            {
                int3 pos2 = { pos.x + n - sc, 
                              pos.y + m - sc, 
                              pos.z + k - sc };
                int v1 = getValue(inputBuffer, pos2, sizes);

                if(v1 < 0) continue;
                if(abs(v1-v0) > rc) continue;
                //if(isOutsideBounds(sizes, pos2)) continue;

                float w = spatialKernel[m] * spatialKernel[n] * rangeKernel[v1 - v0 + rc];
                v += v1 * w;
                total += w;
            }
        }
    }
    setValue(outputBuffer, pos, sizes, (int)(v/total));
}

kernel void BilateralFilter(global const uchar* inputBuffer, 
                            global uchar* outputBuffer,  
                            const int imageWidth, 
                            const int imageHeight, 
                            const int imageDepth,
                            global const float* spatialKernel,  
                            const int spatialSize,
                            global const float* rangeKernel, 
                            const int rangeSize)
{

    const int3 sizes = { imageWidth, imageHeight, imageDepth };
    int3 pos = { get_global_id(0), get_global_id(1), 0 };
    
    if(isOutsideBounds(pos, sizes)) return;

    for(int i = 0; i < imageDepth; ++i)
    {
        int3 pos = { get_global_id(0), get_global_id(1), i };
        DaniBilateralFilter3D(pos, 
                              sizes, 
                              inputBuffer, 
                              outputBuffer,     
                              spatialKernel, 
                              spatialSize, 
                              rangeKernel, 
                              rangeSize);
    }    
}
