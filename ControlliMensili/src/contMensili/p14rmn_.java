package contMensili;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.StringTokenizer;

/*************************************************************************
 *  Compilation:  javac SE_MTF_2xNyquist.java
 *  Execution:    java SE_MTF_2xNyquist
 *
 *  Calculates the Modulation Transfer Function (MTF) for an image of f rows and c columns of pixels selected. 
 *	For FFT requirements, uses a sample of power of 2 rows for both the calculation of the MTF and the SPP.
 *	
 *	Calcula la Funci�n de Transferencia de Modulaci�n (MTF) para una imagen de un tama�o f filas y 
 *	c columnas de pixels seleccionados. 
 *	Por requisitos de la fft, se utiliza una muestra de hileras potencia de 2 tanto para el calculo 
 *	de la MTF como de la SPP.
 *  
 *
 *  Limitations
 *  -----------
 *   * TIFF image only
 *  
 *  
 *************************************************************************/
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.NewImage;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.ProfilePlot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import utils.AboutBox;
import utils.MyInput;
import utils.MyLine;
import utils.MyLog;
import utils.MyVersionUtils;
import utils.UtilAyv;

public class p14rmn_ implements PlugIn {

	Frame frame;
	ImagePlus imp, impOriginal;
	ProfilePlot plotESF;
	Plot plotResult;
	int k;
	int i;
	int selecWidth;
	int selecHeight;
	int sppLength;
	double[] ESFLinea;
	double[][] ESFArray;
	double[][] LSFArray;
	double[] Vector;
	double[][] Array;
	double[][] ArrayF;
	double[][] ESFArrayF;
	double[][] LSFArrayF;
	double[][] PosMax;
	double[] ESFVector;
	double[] LSFVector;
	double[] LSFDVector;
	double[] MTFVector;
	double[] SPPVector;
	double[] Max;
	String title;
	Roi roi;
	int optWidth;
	int optHeight;
	int sChannel;
	int sFrequnits;
	int type;
	boolean isStack;
	int roiType;
	int sSize;
	boolean cancel;
	boolean restart;
	int bit;
	int yMax;
	double mmSensors = 0.0;
	int nPhotodetectors = 0;
	double ny = 1;
	int level = 0;
	private static final int ABORT = 1;

	public static String VERSION = "MTF metodo SLANTED EDGE";

	private String TYPE = " >> CONTROLLO MTF SLANTED EDGE____";

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.txt
	 */

	private static final boolean debug = true;

	private static int timeout = 0;
	public static final boolean forcesilent = false;

	public static final boolean blackbox = false;
	public static String blackpath = "";
	public static String blackname = "";
	public static String blacklog = "";

	// Message that asks the user what to do
	public void run(String args) {

		UtilAyv.setMyPrecision();

		Count c1 = new Count();
		if (!c1.jarCount("iw2ayv_"))
			return;

		String className = this.getClass().getName();

		VERSION = className + "_build_" + MyVersion.getVersion() + "_iw2ayv_build_" + MyVersionUtils.getVersion();

		if (IJ.versionLessThan("1.43k"))
			return;

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			// autoMenu(args);
		}

	}

	/**
	 * Menu funzionamento manuale (chiamato dal menu di ImageJ)
	 * 
	 * @param preset
	 *            da utilizzare per eventuali test
	 * @param testDirectory
	 *            da utilizzare per eventuali test
	 * @return
	 */

	public int manualMenu(int preset, String testDirectory) {

		boolean retry = false;
		boolean step = false;
		boolean fast = false;
		String[] titolo = { "Controllo MTF", "con SLANTED EDGE " };
		int mode = 0;
		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				new AboutBox().about(titolo, MyVersion.CURRENT_VERSION);

				retry = true;
				break;
			case 3:
				timeout = MyInput.myIntegerInput("Ritardo avanzamento (0 = infinito)", "      [msec]", 1000, 0);

				// selfTestMenu();
				retry = true;

				break;
			case 4:
				// step = true;
				mode = 3;
			case 5:
				if (mode == 0)
					mode = 2;
				String path1 = UtilAyv.imageSelection("SELEZIONARE IMMAGINE...");
				if (path1 == null)
					return 0;
				// boolean autoCalled = false;
				// boolean verbose = true;
				// boolean test = false;
				// boolean silent = false;

				mainMTF(path1, "0", "", mode, 0);

				UtilAyv.afterWork();
				retry = true;
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	public ResultsTable mainMTF(String path1, String autoArgs, String info10, int mode, int timeout) {

		boolean accetta = false;
		// boolean abort = false;
		// boolean demo = false;
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		boolean fast = false;
		boolean silent = false;

		if (forcesilent)
			mode = 0;

		switch (mode) {
		case 0:
			// questo e' il caso del funzionamento silent: niente a display
			silent = true;
			break;
		case 1:
			// questo e' il caso del funzionamento fast: tutto va via liscio,
			// solo il minimo sindacale a display
			autoCalled = true;
			fast = true;
			break;
		case 2:
			// questo e' il modo di funzionamento manuale
			verbose = true;
			step = true;
			break;
		case 3:
			// questo e' il modo di funzionamento manuale passo per passo conil
			// valore di tutte le operazioni intermedie
			verbose = true;
			step = true;
			break;
		case 4:
			verbose = true;
			break;
		case 10:
			verbose = true;
			test = true;
			break;
		}

		ImagePlus imp1 = null;
		if (verbose)
			imp1 = UtilAyv.openImageMaximized(path1);
		else
			imp1 = UtilAyv.openImageNoDisplay(path1, true);

		int lato = 140;
		manualSearchPosition(imp1, lato);
		// boolean broken = false;
		calculateMTF(imp1);
		ResultsTable rt = null;
		return rt;
	}

	public void calculateMTF(ImagePlus imp2) {
		title = imp2.getTitle();
		cancel = false;
		restart = false;
		level = 4;
		// openImage();

		// Get the selection
		Roi roi2 = imp2.getRoi();
		if (roi2 == null) {
			imp2.setRoi(0, 0, imp2.getWidth(), imp2.getHeight());
			roi2 = imp2.getRoi();
			IJ.showMessageWithCancel("Warning", "All image selected");
		}
		do {
			cancel = false;
			restart = false;
			level = level - 1;
			fixedOptions(level);
			if (sSize < 32) {
				MyLog.waitHere("non riesco a calcolare la MTF!!!");
				cancel = true;
			}
			// if (cancel == false) {
			// options();
			// }
			// if (cancel == false) {

			generateESFArray("ESF Plot", imp2, roi2);
			if (restart) {
				continue;
			}

			generateLSFArray("LSF Plot", ESFArray);
			calculateMax();

			ESFArrayF = alignArray(ESFArray);
			if (restart) {
				continue;
			}

			if (cancel == false) {
				LSFArrayF = alignArray(LSFArray);
				if (restart) {
					continue;
				}
			}
			if (cancel == false) {
				ESFVector = averageVector(ESFArrayF);
			}
			if (cancel == false) {
				LSFVector = averageVector(LSFArrayF);

				int aura = (LSFVector.length * 2);
				LSFDVector = new double[aura];
				int j = 0;
				int aura2 = (LSFVector.length);

				for (i = 0; i < (LSFDVector.length - 3); i++) {

					if (i % 2 == 0) {
						LSFDVector[i] = LSFVector[j];
						j = j + 1;
					} else {
						LSFDVector[i] = ((0.375 * LSFVector[(j - 1)]) + (0.75 * LSFVector[(j)])
								- (0.125 * LSFVector[(j + 1)]));
					}
				}

				LSFDVector[i] = ((LSFVector[j - 1] + LSFVector[j]) * 0.5);
				LSFDVector[i + 1] = LSFVector[j];
				LSFDVector[i + 2] = LSFVector[j];

				int indexMax = 0;
				double valorMax = LSFDVector[0];
				for (int i = 0; i < LSFDVector.length; i++) {
					if (valorMax < LSFDVector[i]) {
						indexMax = i;
						valorMax = LSFDVector[i];
					}
				}
				i = indexMax;
				LSFDVector[i - 1] = ((LSFDVector[i - 2] + LSFDVector[i]) * 0.5);

				MTFVector = fftConversion(LSFDVector, "MTF");
				Max = obtenerMax();
				SPPVector = fftConversion(Max, "SPP");

				// -****************************************************************-S'hauria
				// d'intentar que no qued�ssin superposats, sin� en escala,
				// cada un 10 m�s avall

				generatePlot(MTFVector, "MTF");
				generatePlot(LSFVector, "LSF");
				generatePlot(ESFVector, "ESF");
				generatePlot(SPPVector, "SPP");
			}
		} while (restart);
		MyLog.waitHere("USCITA!!");
	}

	void generateStack() {

		if (isStack == true && sChannel != 3) {
			ImageConverter ic = new ImageConverter(imp);
			ic.convertToRGBStack();
			ImageStack is;
			is = imp.getStack();
		}
	}

	public static void manualSearchPosition(ImagePlus imp1, int lato) {
		imp1.setRoi((imp1.getWidth() - lato) / 2, (imp1.getHeight() - lato) / 2, lato, lato);
		MyLog.waitHere("posizionare la ROI sullo SLANTED EDGE, in modo che la parte nera sia A SINISTRA e premere OK");
	}

	void fixedOptions(int level) {
		sFrequnits = 1;
		switch (level) {
		case 3:
			sSize = 128;
			break;
		case 2:
			sSize = 64;
			break;
		case 1:
			sSize = 32;
			break;
		case 0:
			sSize = 0;
			break;
		}

		IJ.showMessage("size= " + sSize);
		return;
	}

	void options() {

		GenericDialog gd = new GenericDialog("MTF - Options", frame);

		// User can choose the units
		// gd.addDialogListener(this);
		String[] frequnits = new String[3];
		frequnits[0] = "Absolute (lp/mm)";
		frequnits[1] = "Relative (cycles/pixel)";
		frequnits[2] = "Line widths per picture height (LW/PH)";
		gd.addChoice("Frequency units:", frequnits, frequnits[1]);

		// Input data
		gd.addNumericField("Sensor size (mm): ", mmSensors, 1);
		gd.addNumericField("Number of photodetectors: ", nPhotodetectors, 0);
		((Component) gd.getNumericFields().get(0)).setEnabled(false);
		((Component) gd.getNumericFields().get(1)).setEnabled(false);

		// The user can choose the sample width
		String[] sampleSize = new String[5];
		sampleSize[0] = "32";
		sampleSize[1] = "64";
		sampleSize[2] = "128";
		sampleSize[3] = "256";
		sampleSize[4] = "512";
		gd.addChoice("Sample size (pixels):", sampleSize, sampleSize[0]);

		// If is a greyscale image there is no options avaliable
		if (isStack == false) {
			gd.addMessage("This is a greyscale image, no options avaliable");
		}
		// If is a RGB image, user can choose each channel or the channels
		// average to calculate MTF
		else {
			gd.addMessage("This is a three channel image, select an option");
			String[] channel = new String[4];
			channel[0] = "Red Channel";
			channel[1] = "Green Channel";
			channel[2] = "Blue Channel";
			channel[3] = "Channels average";
			gd.addChoice("Channel", channel, channel[3]);
		}

		// Show General Dialog (MTF Options)
		gd.showDialog();

		// Ends the proccess
		if (gd.wasCanceled()) {
			cancel = true;
			// cleanImage();
			return;
		}

		// gd.addDialogListener(this);

		// Set the stat of the NumericText
		mmSensors = (double) gd.getNextNumber();
		nPhotodetectors = (int) gd.getNextNumber();
		sFrequnits = gd.getNextChoiceIndex();

		// Frequency units
		if (sFrequnits == 0) {
			ny = (nPhotodetectors / mmSensors);
		}
		if (sFrequnits == 1) {
			ny = 1;
		}
		if (sFrequnits == 2) {
			ny = (nPhotodetectors * 2);
		}

		// Save options
		sSize = gd.getNextChoiceIndex();
		String stringSize = sampleSize[sSize];
		sSize = Integer.parseInt(stringSize);

		if (isStack == false) {
			sChannel = 0;
		} else {
			sChannel = gd.getNextChoiceIndex();
		}

		if (isStack == true && sChannel != 3) {
			generateStack();
			imp.setSlice(sChannel + 1);
		}
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		sFrequnits = gd.getNextChoiceIndex();

		if (sFrequnits == 1) {
			((Component) gd.getNumericFields().get(0)).setEnabled(false);
			((Component) gd.getNumericFields().get(1)).setEnabled(false);
		}
		if (sFrequnits == 0) {
			((Component) gd.getNumericFields().get(0)).setEnabled(true);
			((Component) gd.getNumericFields().get(1)).setEnabled(true);
		}
		if (sFrequnits == 2) {
			((Component) gd.getNumericFields().get(0)).setEnabled(false);
			((Component) gd.getNumericFields().get(1)).setEnabled(true);
		}

		return true;
	}

	// The grey values of the line selections are tipped in a Array
	void generateESFArray(String title, ImagePlus imp, Roi roi) {

		Rectangle r = roi.getBounds();
		selecWidth = r.width;
		selecHeight = r.height;

		if (sSize >= selecWidth) {
			IJ.showMessage("Error", "sample size is bigger than selection width\nProcess canceled");
			restart = true;
			return;
		}

		int selectX = r.x;
		int selectY = r.y;
		int selectXFin = selectX + selecWidth;
		int selectYFin = selectY + selecHeight;
		ESFLinea = new double[selecWidth];
		ESFArray = new double[selecHeight][selecWidth];
		for (k = 0; k < selecHeight; k++) {
			// select line
			IJ.makeLine(selectX, k + selectY, selectXFin - 1, k + selectY);
			// save values
			plotESF = new ProfilePlot(imp);
			ESFLinea = plotESF.getProfile();
			// Load Array ESF
			for (i = 0; i < selecWidth; i++) {
				ESFArray[k][i] = ESFLinea[i];
			}
		}
	}

	void generateLSFArray(String title, double[][] ESFArray) {

		LSFArray = new double[selecHeight][selecWidth];

		for (k = 0; k < selecHeight; k++) {
			for (i = 0; i < selecWidth - 1; i++) {
				LSFArray[k][i] = ESFArray[k][i + 1] - ESFArray[k][i];
			}
		}
	}

	public double[][] alignArray(double[][] Array) {

		ArrayF = new double[selecHeight][sSize];
		int ini;
		int fin;

		// IJ.log("dimensioni Array sorgente= [" + Array.length + "] [" +
		// Array[0].length + "]");
		// IJ.log("dimensioni ArrayF allineando= [" + ArrayF.length + "] [" +
		// ArrayF[0].length + "]");
		// IJ.log("selecHeight= " + selecHeight);
		// MyLog.waitHere();

		// Create new array aligned
		for (k = 0; k < selecHeight; k++) {

			// Initial and end positions of k-line
			ini = (int) PosMax[k][2];
			fin = (int) PosMax[k][3];

			if (ini < 0 || fin > Array.length) {
				restart = true;
				return null;
			}
			if (ini < 0 || fin > Array[0].length) {
				restart = true;
				return null;
			}

			for (i = ini; i < fin; i++) {
				ArrayF[k][i - ini] = Array[k][i];
			}
		}
		// final array - from 32 to 512 positions
		return ArrayF;
	}

	// Calculate maximum value and find 32 positions to align
	void calculateMax() {

		PosMax = new double[selecHeight][4];
		int posMax;
		int halfSize;
		halfSize = sSize / 2;
		for (k = 0; k < selecHeight - 1; k++) {
			posMax = 0;
			for (i = 0; i < selecWidth - 1; i++) {
				if (LSFArray[k][posMax] < LSFArray[k][i] || LSFArray[k][posMax] == LSFArray[k][i]) {
					posMax = i;
				}
			}

			// The maximum value position on the line
			PosMax[k][0] = posMax;
			// The maximum value on the line
			PosMax[k][1] = LSFArray[k][posMax];
			// Starting and ending position to align maximum values
			PosMax[k][2] = PosMax[k][0] - halfSize;
			PosMax[k][3] = PosMax[k][0] + halfSize;
		}
	}

	public double[] averageVector(double[][] Array) {

		double result;
		int j;
		double[] Vector = new double[sSize];

		// Average of all linear ESF/LSF
		for (i = 0; i < sSize; i++) {
			result = 0;
			// Average of all rows i-position
			for (k = 0; k < selecHeight; k++) {
				result = result + Array[k][i];
			}
			result = result / selecHeight;
			Vector[i] = result;
		}
		return Vector;
	}

	public int longPotDos(int length) {
		int N;
		double log = Math.log(length) / Math.log(2);
		N = (int) Math.pow(2, (int) Math.floor(log));
		return N;
	}

	public double[] obtenerMax() {
		int N = longPotDos(selecHeight);
		Max = new double[N];
		for (k = 0; k < N; k++) {
			Max[k] = PosMax[k][1];
		}
		return Max;
	}

	void generatePlot(double[] Vector, String plot) {

		double[] xValues;
		String ejeX = "pixel";
		String ejeY = "";
		String allTitle = "";
		ImageProcessor imgProc;

		// If MTF plot, calculate the scale of cycles per pixel for x-axis
		// values
		xValues = calculateXValues(Vector, plot);

		// plot titles
		if (plot == "ESF") {
			ejeY = "Grey Value";
		}

		if (plot == "LSF") {
			ejeY = "Grey Value / pixel";
		}

		if (plot == "MTF") {
			ejeY = "Modulation Factor";

			// Units
			if (sFrequnits == 0) {
				ejeX = "lp/mm";
			}
			if (sFrequnits == 1) {
				ejeX = "Cycles/Pixel";
			}
			if (sFrequnits == 2) {
				ejeX = "Line Width/Picture Height";
			}
		}

		if (plot == "SPP") {
			ejeY = "SPP";
		}

		allTitle = plot + "_" + title;
		plotResult = new Plot(allTitle, ejeX, ejeY, xValues, Vector);

		// plot limits
		if (plot == "ESF") {
			plotResult.setLimits(1, Vector.length, 0, yMax);
		}

		if (plot == "LSF") {
			plotResult.setLimits(1, Vector.length, 0, yMax);
		}

		if (plot == "MTF") {
			plotResult.setLimits(0, ny, 0, 1);
		}

		if (plot == "SPP") {
			plotResult.setLimits(1, Vector.length, 0, 1);
		}

		plotResult.draw();
		plotResult.setLimitsToFit(true);
		plotResult.show();
	}

	public double[] calculateXValues(double[] Vector, String plot) {

		int N = Vector.length;
		double[] xValues = new double[N];

		if (plot == "MTF") {
			xValues[0] = 0;
			// Scale of values for x-axis
			for (i = 1; i < N; i++) {
				// xValues[i]=xValues[i-1]+(0.5/(N-1));
				xValues[i] = xValues[i - 1] + (ny / (N - 1));
			}
		} else {
			for (i = 0; i < N; i++) {
				xValues[i] = i + 1;
			}
		}
		return xValues;
	}

	// data type conversion from complex to double, to implement fft
	public double[] fftConversion(double[] Vector, String plot) {

		// Only half are necessary
		int N = Vector.length;
		int M = Vector.length / 2;
		double divisor;
		Complex[] ArrayComplex = new Complex[N];
		Complex[] VectorFFTC = new Complex[N];
		double[] VectorFFTD = new double[M];

		for (i = 0; i < N; i++) {
			// A double array is converted into a complex array ; imaginary
			// part=0
			ArrayComplex[i] = new Complex(Vector[i], 0);
		}
		// FFT operation
		VectorFFTC = fft(ArrayComplex);

		if (plot == "SPP") {
			// Reject the first one
			for (i = 1; i < M; i++) {
				// absolute value (module)
				VectorFFTD[i - 1] = VectorFFTC[i].abs() / VectorFFTC[1].abs();
			}
		} else {
			for (i = 0; i < M; i++) {
				// absolute value (module)
				VectorFFTD[i] = VectorFFTC[i].abs() / VectorFFTC[0].abs();
			}
		}

		// Normalize
		if (plot == "SPP") {
			divisor = findMaxSPP(VectorFFTD);
		} else {
			divisor = VectorFFTD[0];
		}

		for (i = 0; i < M; i++) {
			VectorFFTD[i] = VectorFFTD[i] / divisor;
		}
		return VectorFFTD;
	}

	public double findMaxSPP(double[] Vector) {
		double max = 0;
		double value;
		for (int i = 0; i < Vector.length; i++) {
			if (Vector[i] > max)
				max = Vector[i];
		}

		return max;
	}

	public class Complex {
		private final double re; // the real part
		private final double im; // the imaginary part

		// create a new object with the given real and imaginary parts
		public Complex(double real, double imag) {
			this.re = real;
			this.im = imag;
		}

		// return a string representation of the invoking object
		public String toString() {
			return re + " + " + im + "i";
		}

		// return a new object whose value is (this + b)
		public Complex plus(Complex b) {
			Complex a = this; // invoking object
			double real = a.re + b.re;
			double imag = a.im + b.im;
			Complex sum = new Complex(real, imag);
			return sum;
		}

		public Complex minus(Complex b) {
			Complex a = this;
			double real = a.re - b.re;
			double imag = a.im - b.im;
			Complex diff = new Complex(real, imag);
			return diff;
		}

		// return a new object whose value is (this * b)
		public Complex times(Complex b) {
			Complex a = this;
			double real = a.re * b.re - a.im * b.im;
			double imag = a.re * b.im + a.im * b.re;
			Complex prod = new Complex(real, imag);
			return prod;
		}

		// return |this|
		public double abs() {
			return Math.sqrt(re * re + im * im);
		}
	}

	public Complex[] fft(Complex[] x) {

		int N = x.length;
		Complex[] y = new Complex[N];

		// base case
		if (N == 1) {
			y[0] = x[0];
			return y;
		}

		// radix 2 Cooley-Tukey FFT
		if (N % 2 != 0)
			throw new RuntimeException("N is not a power of 2");
		Complex[] even = new Complex[N / 2];
		Complex[] odd = new Complex[N / 2];
		for (int k = 0; k < N / 2; k++)
			even[k] = x[2 * k];
		for (int k = 0; k < N / 2; k++)
			odd[k] = x[2 * k + 1];

		Complex[] q = fft(even);
		Complex[] r = fft(odd);

		for (int k = 0; k < N / 2; k++) {
			double kth = -2 * k * Math.PI / N;
			Complex wk = new Complex(Math.cos(kth), Math.sin(kth));
			y[k] = q[k].plus(wk.times(r[k]));
			y[k + N / 2] = q[k].minus(wk.times(r[k]));
		}
		return y;
	}

	public Roi positionSearch(ImagePlus imp1, double minSizeInPixel, double maxSizeInPixel, double minCirc,
			double maxCirc) {

		ImagePlus imp2 = applyThreshold(imp1);
		UtilAyv.showImageMaximized(imp2);

		Overlay over1 = new Overlay();
		imp1.setOverlay(over1);
		Overlay over2 = new Overlay();
		imp2.setOverlay(over2);

		int options = ParticleAnalyzer.SHOW_OUTLINES + ParticleAnalyzer.ADD_TO_MANAGER;
		boolean excludeEdges = true;
		if (excludeEdges)
			options = ParticleAnalyzer.SHOW_OUTLINES + ParticleAnalyzer.ADD_TO_MANAGER
					+ ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;

		int measurements = 0;

		ResultsTable rt0 = new ResultsTable();
		RoiManager rm0 = new RoiManager(false);
		ParticleAnalyzer pa1 = new ParticleAnalyzer(options, measurements, rt0, minSizeInPixel, maxSizeInPixel, minCirc,
				maxCirc);
		ParticleAnalyzer.setRoiManager(rm0);
		pa1.setHideOutputImage(true);
		pa1.analyze(imp2);
		Roi roi0 = null;

		int num = rm0.getCount();
		Roi[] vetRoi = rm0.getRoisAsArray();
		roi0 = vetRoi[0];

		// MyLog.waitHere("" + roi0.getDebugInfo());

		imp2.setRoi(roi0);
		imp2.updateAndDraw();

		Rectangle rect = roi0.getBounds();
		int rx = rect.x;
		int ry = rect.y;
		int w = rect.width;
		int h = rect.height;
		imp2.setRoi(rx, ry, w, h);
		imp2.getRoi().setStrokeColor(Color.red);
		over2.addElement(imp2.getRoi());

		// imp2.setRoi(new Line(rx, ry, rx, ry + h));
		imp2.setRoi(new Line(rx + w - 1, ry + h, rx + w - 1, ry));
		double[][] aaa = MyLine.decomposer(imp2);
		// MyLog.waitHere();
		boolean search = true;
		double find1x = 0;
		double find1y = 0;
		for (int i1 = 0; i1 < aaa[0].length; i1++) {
			// IJ.log("i1:" + i1 + " x:" + (int) aaa[0][i1] + " y:" + (int)
			// aaa[1][i1] + " val:" + (int) aaa[2][i1]);
			if ((int) aaa[2][i1] == 0 && search) {
				search = false;
				find1x = aaa[0][i1];
				find1y = aaa[1][i1];
			}
		}
		imp2.setRoi(new OvalRoi(find1x - 8, find1y - 8, 16, 16));
		Roi roi1 = imp2.getRoi();
		roi1.setFillColor(Color.green);
		roi1.setStrokeColor(Color.green);
		over2.addElement(imp2.getRoi());
		// MyLog.waitHere("find1x= " + find1x + " find1y= " + find1y);

		search = true;
		double find2x = 0;
		double find2y = 0;
		imp2.setRoi(new Line(rx + w - 1, ry + h - 1, rx, ry + h - 1));
		double[][] bbb = MyLine.decomposer(imp2);
		// MyLog.waitHere();

		for (int i1 = 0; i1 < bbb[0].length; i1++) {
			// IJ.log("i1:" + i1 + " x:" + (int) bbb[0][i1] + " y:" + (int)
			// bbb[1][i1] + " val:" + (int) bbb[2][i1]);
			if ((int) bbb[2][i1] == 0 && search) {
				search = false;
				find2x = bbb[0][i1];
				find2y = bbb[1][i1];
			}
		}
		imp2.setRoi(new OvalRoi(find2x - 8, find2y - 8, 16, 16));
		// double

		roi1 = imp2.getRoi();
		roi1.setFillColor(Color.green);
		roi1.setStrokeColor(Color.green);
		over2.addElement(imp2.getRoi());

		// MyLog.waitHere("find2x= " + find2x + " find2y= " + find2y);

		int lato = (int) (Math.abs(find1y - find2y));

		int xsel = (int) Math.round(find2x);
		int ysel = (int) Math.round(find1y);

		imp2.setRoi(xsel, ysel, lato, lato);
		roi1 = imp2.getRoi();
		roi1.setStrokeColor(Color.green);
		over2.addElement(imp2.getRoi());

		MyLog.waitHere("FINITO DENTRO");

		ImagePlus imp3 = imp2.crop();
		UtilAyv.showImageMaximized(imp3);

		return null;

	}

	public static ImagePlus applyThreshold(ImagePlus imp1) {
		int slices = 1;
		ImageProcessor ip1 = imp1.getProcessor();
		short[] pixels1 = (short[]) ip1.getPixels();
		int threshold = ip1.getAutoThreshold();
		ImagePlus imp2 = NewImage.createByteImage("Thresholded", imp1.getWidth(), imp1.getHeight(), slices,
				NewImage.FILL_BLACK);
		ByteProcessor ip2 = (ByteProcessor) imp2.getProcessor();
		byte[] pixels2 = (byte[]) ip2.getPixels();
		for (int i1 = 0; i1 < pixels2.length; i1++) {
			if (pixels1[i1] >= threshold) {
				pixels2[i1] = (byte) 255;
			} else {
				pixels2[i1] = (byte) 0;
			}
		}
		ip2.resetMinAndMax();
		return imp2;
	}

}
