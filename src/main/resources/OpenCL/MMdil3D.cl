bool isOutsideBounds(int3 sizes, int3 pos)
{
     if(pos.x < 0 || pos.y < 0 || pos.z < 0 ||
        pos.x >= sizes.x || pos.y >= sizes.y || pos.z >= sizes.z)
        return -1;
    
    size_t index = pos.x + pos.y*sizes.x + pos.z*sizes.x*sizes.y;
    
    if(index >= sizes.x*sizes.y*sizes.z)
        return true;
    
    return false;
}

int getValue(global const uchar* buffer, int3 sizes, int3 pos)
{
     if(pos.x < 0 || pos.y < 0 || pos.z < 0 ||
        pos.x >= sizes.x || pos.y >= sizes.y || pos.z >= sizes.z)
        return -1;
    
    size_t index = pos.x + pos.y*sizes.x + pos.z*sizes.x*sizes.y;
    
    if(index >= sizes.x*sizes.y*sizes.z)
        return -1;
    
    return buffer[index];
}

void setValue(global uchar* buffer, int3 sizes, int3 pos, int value)
{
    size_t index = pos.x + pos.y*sizes.x + pos.z*sizes.x*sizes.y;
    buffer[index] = value;
}


kernel void MMdil3DInit(global const uchar* inputBuffer, global uchar* outputBuffer, int3 pos, int3 sizes, global const uchar* structElem, int3 structElemSizes)
{

    if(isOutsideBounds(sizes, pos)) return;

    int v0 =  getValue(inputBuffer, sizes, pos);
    int scw = (int)structElemSizes.x / 2;
    int sch = (int)structElemSizes.y / 2;
    int scd = (int)structElemSizes.z / 2;
    
    int maxx = 0;
    

    for (int n = 0; n < structElemSizes.z; ++n)
    {
        for (int m = 0; m < structElemSizes.y; ++m)
        {
            for (int k = 0; k < structElemSizes.x; ++k)
            {
                int3 pos2 = { pos.x + k - scw, pos.y + m - sch, pos.z + n - scd };
                int3 pos3 = { k , m , n };
                int v =  getValue(inputBuffer, sizes, pos2);
                int w = getValue(structElem, structElemSizes, pos3) ;
                if ((w>0)&&(maxx<v))             // dilation gets the max{f(x+s,y+t)} for s=t=structElem
                        maxx = v;

            }
        }
    }

    setValue(outputBuffer, sizes, pos, maxx);
}

kernel void MMdil3DFilterInit(global const uchar* inputBuffer, 
                         global uchar* outputBuffer, 
                         int imageWidth, 
                         int imageHeight, 
                         int imageDepth, 
                         global const uchar* structElem, 
                         int structElemWidth,
                         int structElemHeight,
                         int structElemDepth,
                         int startOffset,
                         int endOffset)
{
    
    int3 sizes = { imageWidth, imageHeight, imageDepth};
    int3 structElemSizes = {structElemWidth,structElemHeight,structElemDepth};
    for(int i = 0; i < imageDepth; ++i)
    {
        int3 pos = { get_global_id(0), get_global_id(1), i };
        MMdil3DInit(inputBuffer, outputBuffer, pos, sizes, structElem, structElemSizes);
    }
}


kernel void MMdil3D(global const uchar* inputBuffer, global const uchar* tmpBuffer, global uchar* outputBuffer, int3 pos, int3 sizes, global const uchar* structElem, int3 structElemSizes)
{

    if(isOutsideBounds(sizes, pos)) return;

    int v0 =  getValue(inputBuffer, sizes, pos);
    int scw = (int)structElemSizes.x / 2;
    int sch = (int)structElemSizes.y / 2;
    int scd = (int)structElemSizes.z / 2;
    
    int maxx = 0;
    

    for (int n = 0; n < structElemSizes.z; ++n)
    {
        for (int m = 0; m < structElemSizes.y; ++m)
        {
            for (int k = 0; k < structElemSizes.x; ++k)
            {
                int3 pos2 = { pos.x + k - scw, pos.y + m - sch, pos.z + n - scd };
                int3 pos3 = { k , m , n };
                int v =  getValue(inputBuffer, sizes, pos2);
                int w = getValue(structElem, structElemSizes, pos3) ;
                if ((w>0)&&(maxx<v))             // dilation gets the max{f(x+s,y+t)} for s=t=structElem
                        maxx = v;

            }
        }
    }

    int tmpv = getValue(tmpBuffer, sizes, pos);
    if(maxx < tmpv)
        maxx = tmpv;

    setValue(outputBuffer, sizes, pos, maxx);
}

kernel void MMdil3DFilter(global const uchar* inputBuffer, 
                         global uchar* tmpBuffer, 
                         global uchar* outputBuffer, 
                         int imageWidth, 
                         int imageHeight, 
                         int imageDepth, 
                         global const uchar* structElem, 
                         int structElemWidth,
                         int structElemHeight,
                         int structElemDepth,
                         int startOffset,
                         int endOffset)
{
    /// let depth go beyond buffer..
    int3 sizes = { imageWidth, imageHeight, imageDepth };
    int3 structElemSizes = {structElemWidth,structElemHeight,structElemDepth};
    for(int i = 0; i < imageDepth; ++i)
    {
        int3 pos = { get_global_id(0), get_global_id(1), i };
        MMdil3D(inputBuffer, tmpBuffer, outputBuffer, pos, sizes, structElem, structElemSizes);
    }
}

