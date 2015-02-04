int zoNumBits(int t)
{
    int n;
    for (n=0; t>0;++n)
        t = t >> 1;
    return n;
}

ulong ci(int s, int size, int mask)
{
    ulong d = 0;

    int n_kBits = size;
  
    for (int j=0;j<n_kBits;++j)
    {
        if ((s & 0x1) != 0)
            d |= mask;
        s = s >> 1;
        mask = mask << 3;
    }
    return d;
}

size_t computeIndex(int3 pos, 
                    const int3 sizes,
                    const int3 sizeBits) {
    return ci(pos.x, sizeBits.x, 0x01) | ci(pos.y, sizeBits.y, 0x2) | ci(pos.z, sizeBits.z, 0x4);
}


float dynamicKernel(float radius, int index)
{
    float x = (index + 1 - radius) / (radius * 2) / 0.2;
    return (float)exp(-0.5 * x * x);
}

kernel void makeKernel(float radius, global float* output, int output_size)
{
    size_t i = get_global_id(0);

    if(i >= output_size) return;

    output[i] = dynamicKernel(radius,i);
}

kernel void normalizeKernel(float total, global float* output, int output_size)
{
    size_t i = get_global_id(0);

    if(i >=  output_size) return;

     if (total <= 0.0)
        output[i] = 1.0f / output_size;
     else if (total != 1.0)
        output[i] /= total;
}

bool isOutsideBounds(int3 pos, 
                     const int3 sizes)
{
     if(pos.x < 0 || pos.y < 0 || pos.z < 0 ||
        pos.x >= sizes.x || pos.y >= sizes.y || pos.z >= sizes.z)
        return true;
    return false;
}

int getValue(global const uchar* buffer, 
             int3 pos, 
             const int3 sizes,
             const int3 sizeBits, 
             global const ulong* zIbits, 
             global const ulong* zJbits,
             global const ulong* zKbits)
{

     if(pos.x < 0 || pos.y < 0 || pos.z < 0 ||
        pos.x >= sizes.x || pos.y >= sizes.y || pos.z >= sizes.z)
        return -1;

    size_t index =  zIbits[pos.x] | zJbits[pos.y] | zKbits[pos.z]; //computeIndex(pos, sizes, sizeBits); //zIbits[pos.x] | zJbits[pos.y] | zKbits[pos.z];

    return buffer[index];
}

void setValue(global uchar* buffer, 
              int3 pos, 
              const int3 sizes,
              const int3 sizeBits, 
              int value, 
              global const ulong* zIbits, 
              global const ulong* zJbits,
              global const ulong* zKbits)
{
    size_t index = zIbits[pos.x] | zJbits[pos.y] | zKbits[pos.z]; //computeIndex(pos, sizes, sizeBits);
    buffer[index] = value;
}

void DaniBilateralFilter3D(int3 pos, 
                           int3 sizes,
                           int3 sizeBits,
                           global const uchar* inputBuffer, 
                           global uchar* outputBuffer, 
                           global const float* spatialKernel, 
                           const int spatialSize,
                           global const float* rangeKernel, 
                           const int rangeSize, 
                           global const ulong* zIbits, 
                           global const ulong* zJbits,
                           global const ulong* zKbits)
{
    int v0 =  getValue(inputBuffer, pos, sizes, sizeBits, zIbits, zJbits, zKbits);

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
                int3 pos2 = { pos.x + m - sc, 
                              pos.y + n - sc, 
                              pos.z + k - sc };
                int v1 = getValue(inputBuffer, pos2, sizes, sizeBits, zIbits, zJbits, zKbits);

                if(abs(v1-v0) > rc) continue;
                float w = spatialKernel[m] * spatialKernel[n] * rangeKernel[v1 - v0 + rc];
                v += v1 * w;
                total += w;
            }
        }
    }

    setValue(outputBuffer, pos, sizes, sizeBits, (int)(v / total), zIbits, zJbits, zKbits);
}

kernel void BilateralFilter(global const uchar* inputBuffer, 
                            global uchar* outputBuffer,  
                            const int imageWidth, 
                            const int imageHeight, 
                            const int imageDepth,
                            global const float* spatialKernel,  
                            const int spatialSize,
                            global const float* rangeKernel, 
                            const int rangeSize,
                            global const ulong* zIbits, 
                            global const ulong* zJbits,
                            global const ulong* zKbits)
{

    int3 sizes = { imageWidth, imageHeight, imageDepth };
    int3 sizeBits;
    sizeBits.x = zoNumBits(sizes.x);
    sizeBits.y = zoNumBits(sizes.y);
    sizeBits.z = zoNumBits(sizes.z);

    int3 pos = { get_global_id(0), get_global_id(1), 0 };
    
    if(isOutsideBounds(pos, sizes)) return;
    
    for(int i = 0; i < imageDepth; ++i)
    {
        int3 pos = { get_global_id(0), get_global_id(1), i };

        DaniBilateralFilter3D(pos, 
                              sizes, 
                              sizeBits,
                              inputBuffer, 
                              outputBuffer,     
                              spatialKernel, 
                              spatialSize, 
                              rangeKernel, 
                              rangeSize, 
                              zIbits, 
                              zJbits, 
                              zKbits);
    }    
}
