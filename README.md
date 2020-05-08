#F3D Image Processing and Analysis

F3D is a Fiji plugin, designed for high-resolution 3D image, and written in OpenCL. F3D plugin achieves platform-portable parallelism on modern multi-core CPUs and many-core GPUs. The interface and mechanisms to access F3D accelerated kernes are written in Java to be fully integrated with other tools available within Fiji/ImageJ. F3D delivers several key image-processing algorithms necessary to remove artifacts from micro-tomography data. The algorithms consist of data parallel aware filters that can efficiently utilize resources and can process data out of core and scale efficiently across multiple accelerators. Optimized for data parallel filters, F3D streams data out of core to efficiently manage resources, such as memory, over complex execution sequence of filters. This has greatly expedited several scientific workflows dealing with high-resolution images. F3D preforms two main types of 3D image processing operations: non-linear filtering, such as bilateral and median filtering, and morphological operators (MM) with varying 3D structuring elements.

Update: F3D has been ported to python under the name pyF3D:

https://github.com/dani-lbnl/pyF3D

Contact dani.lbnl@gmail.com for more informations
