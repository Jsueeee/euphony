package co.euphony.rx;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

public abstract class FFTStrategy {

    int fftSize;
    int floatBufferSize;
    int byteBufferSize;
    int shortBufferSize;

    /* recycelSamples & Spectrum are direct buffers for quick processing.
     * That's why they're allocated just once on Constructor.  */
    ByteBuffer recycleSamples;
    FloatBuffer recycleSpectrum;

    public FFTStrategy(int fftSize) {
        this.fftSize = fftSize;
        this.byteBufferSize = fftSize;
        this.floatBufferSize = fftSize >> 1;
        this.shortBufferSize = this.floatBufferSize;

        initialize(fftSize);
        recycleSamples = makeByteBuffer(fftSize);
        recycleSpectrum = makeFloatBuffer(this.floatBufferSize + 1);
    }

    abstract void initialize(int fftSize);

    public FloatBuffer[] makeSpectrum(short[] inputShortSamples) {
        int lenSamples = inputShortSamples.length;
        int cycleCount = 1;
        if(lenSamples > shortBufferSize) {
            cycleCount = lenSamples / shortBufferSize;
        }
       FloatBuffer[] result = new FloatBuffer[cycleCount];

        ShortBuffer sb = makeShortBuffer(shortBufferSize);
        for(int i = 0; i < cycleCount; i++)
        {
            short[] src = Arrays.copyOfRange(inputShortSamples, i * shortBufferSize, (i + 1) * shortBufferSize);
            recycleSpectrum.clear();
            sb.put(src);
            makeSpectrum(sb, recycleSpectrum);
            result[i] = FloatBuffer.allocate(recycleSpectrum.capacity());
            result[i].put(recycleSpectrum);
            result[i].position(recycleSpectrum.position());
            sb.clear();
        }

        sb = null;
        return result;
    }

    public abstract FloatBuffer makeSpectrum(ShortBuffer samples, FloatBuffer spectrum);
    public abstract void finish();

    void releaseAllBuffers() {
        recycleSamples.clear();
        try {
            destroyDirectByteBuffer(recycleSamples);
        } catch(Exception e) {
            e.printStackTrace();
        }
        recycleSamples = null;

        recycleSpectrum.clear();
        recycleSpectrum = null;

        System.gc();
    }

    public int getFFTSize() {
        return fftSize;
    }

    public void setFFTSize(int fftSize) {
        this.fftSize = fftSize;
    }

    public FloatBuffer getSpectrum() {
        return recycleSpectrum;
    }

    private ByteBuffer makeByteBuffer(int numSamples) {
        ByteBuffer buf = ByteBuffer.allocateDirect(numSamples);
        buf.order(ByteOrder.nativeOrder());
        return buf;
    }

    private FloatBuffer makeFloatBuffer(int numSamples) {
        return makeByteBuffer(numSamples * 4).asFloatBuffer();
    }

    private FloatBuffer makeFloatBuffer(float[] inputFloatArray) {
        return makeByteBuffer(inputFloatArray.length * 4).asFloatBuffer().put(inputFloatArray);
    }

    private ShortBuffer makeShortBuffer(int numSamples) {
        return makeByteBuffer(numSamples * 2).asShortBuffer();
    }

    private ShortBuffer makeShortBuffer(short[] inputShortArray) {
        return makeByteBuffer(inputShortArray.length * 2).asShortBuffer().put(inputShortArray);
    }

    /**
     * DirectByteBuffers are garbage collected by using a phantom reference and a
     * reference queue. Every once a while, the JVM checks the reference queue and
     * cleans the DirectByteBuffers. However, as this doesn't happen
     * immediately after discarding all references to a DirectByteBuffer, it's
     * easy to OutOfMemoryError yourself using DirectByteBuffers. This function
     * explicitly calls the Cleaner method of a DirectByteBuffer.
     *
     * @param toBeDestroyed
     *          The DirectByteBuffer that will be "cleaned". Utilizes reflection.
     *
     */
    public static void destroyDirectByteBuffer(ByteBuffer toBeDestroyed)
            throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, SecurityException, NoSuchMethodException {

        Method cleanerMethod = toBeDestroyed.getClass().getMethod("cleaner");
        cleanerMethod.setAccessible(true);
        Object cleaner = cleanerMethod.invoke(toBeDestroyed);
        Method cleanMethod = cleaner.getClass().getMethod("clean");
        cleanMethod.setAccessible(true);
        cleanMethod.invoke(cleaner);
    }


}
