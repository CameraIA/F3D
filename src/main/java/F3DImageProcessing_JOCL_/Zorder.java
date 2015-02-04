package F3DImageProcessing_JOCL_;

import java.nio.LongBuffer;
import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLContext;
import static com.jogamp.opencl.CLMemory.Mem.READ_ONLY;

public class Zorder<T>
{
    private final static long mask1 = 0x1f00000000ffffL; 
    private final static long mask2 = 0x1f0000ff0000ffL; 
    private final static long mask3 = 0x100f00f00f00f00fL; 
    private final static long mask4 = 0x10c30c30c30c30c3L; 
    private final static long mask5 = 0x1249249249249249L;

        
    /* array-order indexing variables and tables */
    /* dims of the array-order mesh. these need not be powers of two */
    private int src_iSize, src_jSize, src_kSize;

    /* dims of the z-order mesh. these will be set to be powers of two
       during the initialization phase */
    private int zo_iSize, zo_jSize, zo_kSize;

    /*
     * when the init routine is called, we precompute some tables that
     * are used later to accelerate construction of both array- and
     * z-order indexing. The app would not normally access these
     * tables directly.
     */
    //private int[] jOffsets, kOffsets;
    //public CLBuffer<IntBuffer> jOffsets, kOffsets;

    /* z-order indexing variables and tables */
    //private int    iShift, jShift, kShift;
    //private int[] zIbits, zJbits, zKbits;
    public CLBuffer<LongBuffer> zIbits, zJbits, zKbits;
    long [] zIbitsT, zJbitsT, zKbitsT;

    /*
     * zoInitOrderIndexing
     * Call this routine to perform initialization before doing any data
     * conversion or data access.
     *
     * Input:
     * iSize, jSize, kSize: these are the dimensions of the input
     * mesh that you'll be working with in subsequent routines.
     *
     * zo: input a pointer to an uninitialized ZOrderStruct. This
     * initialization routine will fill it in with a  bunch of stuff that
     * will be used by later conversion and indexing routines.
     *
     * Returns: a valid ZOrderStruct if successful, NULL otherwise.
    */
    public void
    zoInitZOrderIndexing(int iSize,
		         int jSize,
		         int kSize, CLContext context)
    {
        int i;
        int n_iBits, n_jBits, n_kBits;

        /* build the array order indexing tables first. */
        src_iSize = iSize;
        src_jSize = jSize;
        src_kSize = kSize;

        /* now, build the z-order indexing tables */
        /* next, build the zorder bit masks for each of i, j, k */
    
        zo_iSize = zoRoundUpPowerOfTwo(iSize);
        zo_jSize = zoRoundUpPowerOfTwo(jSize);
        zo_kSize = zoRoundUpPowerOfTwo(kSize);
        
        
        /* malloc space for bitmasks. */
        zIbits = context.createLongBuffer(zo_iSize, READ_ONLY);
        zJbits = context.createLongBuffer(zo_jSize, READ_ONLY);
        zKbits = context.createLongBuffer(zo_kSize, READ_ONLY);

        zIbitsT = new long [zo_iSize];
        zJbitsT = new long [zo_jSize];
        zKbitsT = new long [zo_kSize];

        /*
         * note/limitation: the maximum size of the z-order index is
         * determined by the size of an off_t. On 32-bit machines, it
         * is a maximum of 30 bits, or 10 bits for each of i,j,k (max
         * array dimensions 1024^3). On 64-bit machines, it is a
         * maximum of 21 bits (max array dimension of 2^21 for any of
         * (i,j,k). 
         */

        /* compute the number bits we need to process */
        n_iBits = zoNumBits(zo_iSize);
        n_jBits = zoNumBits(zo_jSize);
        n_kBits = zoNumBits(zo_kSize);

        System.out.println("iBits =" + n_iBits + " " + n_jBits + " " + n_kBits);

        LongBuffer zi = (LongBuffer)zIbits.getBuffer();
        LongBuffer zj = (LongBuffer)zJbits.getBuffer();
        LongBuffer zk = (LongBuffer)zKbits.getBuffer();

        /* now, build the bitmasks */
        {
	        int j;
	        long s, d, mask;

	        for (i=0;i<src_kSize;i++)
	        {
                /// wes' version
	            d = 0;
	            mask = 0x4L; 
	            s = i;
	            for (j=0;j<n_kBits;j++)
	            {
		            if ((s & 0x1L) != 0)
		                d |= mask;
		            s = s >> 1;
		            mask = mask << 3;
	            }
                zk.put(d);
                
                /// mask version 
                long a = i;
                a &= (long)0x1fffff;
                a = (a | a << 32) & mask1;
                a = (a | a << 16) & mask2;
                a = (a | a << 8) & mask3;
                a = (a | a << 4) & mask4;
                a = (a | a << 2) & mask5;

	            //zKbits[i] = d;
                zKbitsT[i] = (a << 2);
	        }

	        /* then the j bits */
	        for (i=0;i<src_jSize;i++)
	        {
                /// wes' version
	            d = 0;
	            mask = 0x2L;
	            s = i;
	            for (j=0;j<n_jBits;j++)
	            {

		            if ((s & 0x1L) != 0)
		                d |= mask;
		            s = s >> 1;
		            mask = mask << 3;
	            }
                zj.put(d);
                
                /// mask version
                long a = i;
                a &= (long)0x1fffff;
                a = (a | a << 32) & mask1;
                a = (a | a << 16) & mask2;
                a = (a | a << 8) & mask3;
                a = (a | a << 4) & mask4;
                a = (a | a << 2) & mask5;
	            //zJbits[i] = d;
                zJbitsT[i] = (a << 1);
	        }

	        /* then the i bits */
	        for (i=0;i<src_iSize;i++)
	        {
                /// wes' version
	            d = 0;
	            mask = 0x1L;
	            s = i;
	            for (j=0;j<n_iBits;j++)
	            {
		            if ((s & 0x1) != 0)
		                d |= mask;
		            s = s >> 1;
		            mask = mask << 3;
	            }
	            zi.put(d);

                /// mask version
                long a = i;
                a &= (long)0x1fffff;
                a = (a | a << 32) & mask1;
                a = (a | a << 16) & mask2;
                a = (a | a << 8) & mask3;
                a = (a | a << 4) & mask4;
                a = (a | a << 2) & mask5;
                zIbitsT[i] = (a);
	        }
        }

        zi.position(0);
        zj.position(0);
        zk.position(0);
    }

    /*
     * ----------------------------------------------------
     * @Name zoNearestPowerOfTwo
     @pstart
     int zoNearestPowerOfTwo (int n)
     @pend

     @astart
     int n - an integer value.
     @aend

     @dstart

     This routine computes the integer that is the closest power of two to
     the input integer "n". The algorithm works only for non-negative
     input.

     This routine will come in handy if you have to scale an image so it's
     size is an even power of two.

     @dend
     * ----------------------------------------------------
     */
    public int
    zoNearestPowerOfTwo (int n)
    {
        int nbits, j;

        j = n;
        for (nbits = 0; j > 0;)
        {
	        j = j >> 1;
	        nbits++;
        }

        if (nbits != 0)
	        nbits--;

        if ((1<<nbits) == n)
	        return(n);
        else
        {
	        int a, b, c;

	        a = (1 << (nbits + 1));	/* 2^j which is greater than n */
	        b = a - n;
	        c = a - (a >> 1);
	        c = c>>1;
	        if (b > c)
	            return(1 << nbits);
	        else
	            return(1 << (nbits + 1));
        }
    }

    /*
     * zoGetIndexZOrder
     *
     * Call this routine to obtain the z-order index of some (i,j,k)
     * element in an array.
     *
     * Input:
     * zo - a pointer to a ZOrderStruct (initialized with
     * zoInitOrderIndexing)
     * i,j,k - off_t's, these are i,j,k index locations
     *
     * Output/returns:
     * -1 on error, or the 1-D index aint a z-order curve of some (i,j,k)
     * location in a 3d array.
     *
     * Notes: use a value of zero for k if computing the index into a 2D
     * array, and values of zero for j and k if computing the index into a
     * 1D array.
     *
    */
    public int
    zoGetIndexZOrder(int i, int j, int k)
    {
        long rval=0;
        /* sanity checks first? */
        rval = zIbits.getBuffer().get(i) | zJbits.getBuffer().get(j) | zKbits.getBuffer().get(k);
        ///System.out.println(rval + " " + i + " " + j + " " + k + " " + (zIbitsT[i]) | (zJbitsT[j]) | (zKbitsT[k])) );
        return (int)rval;
    }

    /* 
     * zoRoundUpPowerOfTwo(off_t n)
     * 
     * return an int that is an even power of two, where 2**i >= n
     */

    public float log2f(float n)
    {
        return (float)(Math.log(n) / Math.log(2));
    }

    public int zoRoundUpPowerOfTwo(int n)
    {
        float f;
        int r,t;

        f = (float)n;
        f = log2f(f);
        f = (float)Math.ceil(f);
        t = (int)f;

        r = 1;
        while (t > 0)
        {
	        r = r << 1;
	        t--;
        }
        
        return r;
    }

    /*
     * zoNumBits(size_t t)
     *
     * Given an input valut, t, return an int that says how many binary
     * digits of precision are needed for that t. For example, if t=8,
     * then zoNumBits() would return 4; if t=7, it would return 3, etc.
     *
     * (it does not count the number of on bits in t)
     */
    private int zoNumBits(int t)
    {
        int n;

        for (n=0; t>0; n++)
	        t = t >> 1;

        return n;
    }
}
