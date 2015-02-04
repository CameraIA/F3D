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

bool isOutsideBounds(int3 pos, 
                     int3 sizes)
{
     if(pos.x < 0 || pos.y < 0 || pos.z < 0 ||
        pos.x >= sizes.x || pos.y >= sizes.y || pos.z >= sizes.z)
        return true;
    return false;
}

int getValue(global const uchar* buffer, 
             int3 pos, 
             int3 sizes, 
             global const long* zIbits, 
             global const long* zJbits,
             global const long* zKbits)
{
    
     if(pos.x < 0 || pos.y < 0 || pos.z < 0 ||
        pos.x >= sizes.x || pos.y >= sizes.y || pos.z >= sizes.z)
        return -1;
    
    size_t index = zIbits[pos.x] | zJbits[pos.y] | zKbits[pos.z];
    return buffer[index];
}

int getValueX(global const uchar* buffer, 
             int3 pos, 
             int3 sizes)
{
    if(pos.x < 0 || pos.y < 0 || pos.z < 0 || 
       pos.x >= sizes.x || pos.y >= sizes.y || pos.z >= sizes.z)
        return -1;
    
    size_t index = pos.x + pos.y*sizes.x + pos.z*sizes.x*sizes.y;
    return buffer[index];
}

void setValue(global uchar* buffer, 
              int3 pos, 
              int3 sizes, 
              int value,
              global const long* zIbits, 
              global const long* zJbits,
              global const long* zKbits)
{
    size_t index = zIbits[pos.x] | zJbits[pos.y] | zKbits[pos.z];
    buffer[index] = value;
}

void MMero3DInit(global const uchar* inputBuffer, 
                 global uchar* outputBuffer, 
                 int3 pos, 
                 int3 sizes, 
                 global const uchar* structElem, 
                 const int3 structElemSizes,
                 global const long* zIbits, 
                 global const long* zJbits,
                 global const long* zKbits)
{
    int scw = (int)structElemSizes.x / 2;
    int sch = (int)structElemSizes.y / 2;
    int scd = (int)structElemSizes.z / 2;
    
    int minn = 65535;    

    for (int n = 0; n < structElemSizes.z; ++n)
    {
        for (int m = 0; m < structElemSizes.y; ++m)
        {
            for (int k = 0; k < structElemSizes.x; ++k)
            {
                int3 pos2 = { pos.x + k - scw, pos.y + m - sch, pos.z + n - scd };
                int3 pos3 = { k , m , n };
                int v =  getValue(inputBuffer, pos2, sizes, zIbits, zJbits, zKbits);
                int w = getValueX(structElem, pos3, structElemSizes) ;
                if ((w>0)&&(minn>v)&&(v>-1))             // erosion gets the min{f(x+s,y+t)} for s=t=structElem
                        minn = v;
            }
        }
    }

    setValue(outputBuffer, pos, sizes, minn, zIbits, zJbits, zKbits);
}

kernel void MMero3DFilterInit(global const uchar* inputBuffer, 
                         global uchar* outputBuffer, 
                         int imageWidth, 
                         int imageHeight, 
                         int imageDepth, 
                         global const uchar* structElem, 
                         int structElemWidth,
                         int structElemHeight,
                         int structElemDepth,
                         int startOffset,
                         int endOffset,
                         global const long* zIbits, 
                         global const long* zJbits,
                         global const long* zKbits)
{
    int3 sizes = { imageWidth, imageHeight, imageDepth};
    int3 structElemSizes = {structElemWidth,structElemHeight,structElemDepth};

    int3 pos = { get_global_id(0), get_global_id(1), 0 };
    if(isOutsideBounds(pos, sizes)) return;

    for(int i = 0; i < imageDepth; ++i)
    {
        int3 pos = { get_global_id(0), get_global_id(1), i };
        MMero3DInit(inputBuffer, 
                    outputBuffer, 
                    pos, 
                    sizes, 
                    structElem, 
                    structElemSizes, 
                    zIbits, 
                    zJbits, 
                    zKbits);
    }
}


/// Middle comparison..

void MMero3D(global const uchar* inputBuffer, 
             global const uchar* tmpBuffer,     
             global uchar* outputBuffer, 
             int3 pos, 
             int3 sizes, 
             global const uchar* structElem, 
             const int3 structElemSizes,
             global const long* zIbits, 
             global const long* zJbits,
             global const long* zKbits)
{

    
    int scw = (int)structElemSizes.x / 2;
    int sch = (int)structElemSizes.y / 2;
    int scd = (int)structElemSizes.z / 2;
    
    int minn = 65535;
    
    for (int n = 0; n < structElemSizes.z; ++n)
    {
        for (int m = 0; m < structElemSizes.y; ++m)
        {
            for (int k = 0; k < structElemSizes.x; ++k)
            {
                int3 pos2 = { pos.x + k - scw, pos.y + m - sch, pos.z + n - scd };
                int3 pos3 = { k , m , n };
                int v =  getValue(inputBuffer, pos2, sizes, zIbits, zJbits, zKbits);
                int w = getValueX(structElem, pos3, structElemSizes) ;
                if ((w>0)&&(minn>v)&&(v>-1)) // erosion gets the min{f(x+s,y+t)} for s=t=structElem
                        minn = v;
            }
        }
    }

    int tmpv = getValue(tmpBuffer, pos, sizes, zIbits, zJbits, zKbits);
    if(minn > tmpv)
        minn = tmpv;
    
    setValue(outputBuffer, pos, sizes, minn, zIbits, zJbits, zKbits);
}

kernel void MMero3DFilter(global const uchar* inputBuffer, 
                         global const uchar* tmpBuffer, 
                         global uchar* outputBuffer, 
                         int imageWidth, 
                         int imageHeight, 
                         int imageDepth, 
                         global const uchar* structElem, 
                         int structElemWidth,
                         int structElemHeight,
                         int structElemDepth, 
                         int startOffset,
                         int endOffset,
                         global const long* zIbits, 
                         global const long* zJbits,
                         global const long* zKbits)
{
    /// let depth go beyond buffer..
    int3 sizes = { imageWidth, imageHeight, imageDepth };
    int3 structElemSizes = {structElemWidth,structElemHeight,structElemDepth};
    int3 pos = { get_global_id(0), get_global_id(1), 0 };
   
    if(isOutsideBounds(pos, sizes)) return;

    for(int i = 0; i < imageDepth; ++i)
    {
        int3 pos = { get_global_id(0), get_global_id(1), i };

        MMero3D(inputBuffer, 
                tmpBuffer, 
                outputBuffer, 
                pos, 
                sizes, 
                structElem, 
                structElemSizes, 
                zIbits, 
                zJbits, 
                zKbits);
    }
}

