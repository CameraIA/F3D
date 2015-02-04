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

void DaniMedianFilter3D(global const uchar* inputBuffer, 
                        global uchar* outputBuffer, 
                        int3 pos, 
                        int3 sizes, 
                        int medianIndex,                   
                        global const long* zIbits, 
                        global const long* zJbits,
                        global const long* zKbits)
{
    int sc = 1; ///shift by 1..

	uchar median;
    uchar histogram[256];
    
    for(int i = 0; i < 256; ++i)
    	histogram[i] = 0;
    
    for (int n = 0; n < 3; ++n)
    {
        for (int m = 0; m < 3; ++m)
        {
            for (int k = 0; k < 3; ++k)
            {
                int3 pos2 = { pos.x + m - sc, 
                              pos.y + n - sc, 
                              pos.z + k - sc };
                int val = getValue(inputBuffer, pos2, sizes, zIbits, zJbits, zKbits);
                if(val >= 0) histogram[val] += 1;
            }
        }
    }

	int x = 0;
			
    for(int i = 0; i < 256; ++i)
    {
    	if(histogram[i] > 0) 
    	{ 
    		x += histogram[i];
    		/// if x gets past median then we set the value..
	    	median = (uchar)i;
            if(x > medianIndex)
	    		break;
	   	}
    }

	//median = getValue(inputBuffer, sizes, pos);
	setValue(outputBuffer, pos, sizes, median, zIbits, zJbits, zKbits);
}

kernel void MedianFilter(global const uchar* inputBuffer, 
                         global uchar* outputBuffer, 
                         const int imageWidth, 
                         const int imageHeight, 
                         const int imageDepth, 
                         int medianIndex,                   
                         global const long* zIbits, 
                         global const long* zJbits,
                         global const long* zKbits)
{

    int3 sizes = { imageWidth, imageHeight, imageDepth };
    int3 pos = { get_global_id(0), get_global_id(1), 0 };

    //if(isOutsideBounds(pos, sizes)) return;

    for(int i = 0; i < imageDepth; ++i)
    {
        int3 pos = { get_global_id(0), get_global_id(1), i };
        DaniMedianFilter3D(inputBuffer, outputBuffer, pos, sizes, medianIndex, zIbits, zJbits, zKbits);
    }
}
