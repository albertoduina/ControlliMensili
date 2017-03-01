package contMensili;

/***
 
* Copied from the source of SE_MTF_2xNyquist.jar (on the imagej site)
 * 
 * Authors: Carles Mitja (carles.mitja@citm.upc.edu), Jaume Escofet, Aura Tacho and Raquel Revuelta.
 * 
 * Adapted by Alberto Duina  01/02/2017
 * 
 * 
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
 *************************************************************************/

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.NewImage;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.ProfilePlot;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.CurveFitter;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import utils.AboutBox;
import utils.MyConst;
import utils.MyInput;
import utils.MyLine;
import utils.MyLog;
import utils.MyMsg;
import utils.MyVersionUtils;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableSequence;
import utils.UtilAyv;

public class p14rmn_ implements PlugIn {

	Frame frame;
	ImagePlus imp, impOriginal;
	ProfilePlot plotESF;
	Plot plotResult;
	int kkk;
	int iii;
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
	static String postmortem = "";
	String pathLog = "";
	Color myColor = Color.black;
	private static final int ABORT = 1;

	public static String VERSION = "MTF metodo SLANTED EDGE";

	private String TYPE = " >> CONTROLLO MTF SLANTED EDGE____";
	private static String fileDir = "";

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
		fileDir = Prefs.get("prefer.string1", "none");

		if (IJ.versionLessThan("1.43k"))
			return;

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
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
				new AboutBox().credits(titolo, "CREDITS to: Carles Mitja", "carles.mitja@citm.upc.edu",
						MyVersion.CURRENT_VERSION);

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

	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p14 riceve " + autoArgs);

		boolean fast = Prefs.get("prefer.fast", "false").equals("true") ? true : false;

		ResultsTable result1 = null;
		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);

		if (nTokens != MyConst.TOKENS1) {
			MyMsg.msgParamError();
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);

		String info10 = (vetRiga[0] + 1) + " / " + TableSequence.getLength(iw2ayvTable) + "   code= "
				+ TableSequence.getCode(iw2ayvTable, vetRiga[0]) + "   coil= "
				+ TableSequence.getCoil(iw2ayvTable, vetRiga[0]);

		String path1 = "";

		if (nTokens == MyConst.TOKENS1) {
			// UtilAyv.checkImages(vetRiga, iw2ayvTable, 2, debug);
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			MyLog.logDebug(vetRiga[0], "P14", fileDir);

			boolean retry = false;
			int mode = 0;

			if (fast) {
				retry = false;
				mode = 1;
				result1 = mainMTF(path1, autoArgs, info10, mode, timeout);

				if (!(result1 == null))
					UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, result1);

				UtilAyv.afterWork();

			} else
				do {
					int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
							TableSequence.getCode(iw2ayvTable, vetRiga[0]),
							TableSequence.getCoil(iw2ayvTable, vetRiga[0]), vetRiga[0] + 1,
							TableSequence.getLength(iw2ayvTable));
					// MyLog.waitHere("AUTO userSelection1=" + userSelection1);

					switch (userSelection1) {
					case ABORT:
						new AboutBox().close();
						return 0;
					case 2:
						new AboutBox().about("Controllo MTF con Slanted Edge", MyVersion.CURRENT_VERSION);
						retry = true;
						break;
					case 3:
						// step = true;
						mode = 2;
					case 4:
						retry = false;
						mode = 3;
						result1 = mainMTF(path1, autoArgs, info10, mode, timeout);

						if (result1 == null) {
							break;
						}

						UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, result1);

						UtilAyv.afterWork();
						break;
					}
				} while (retry);
			new AboutBox().close();
			UtilAyv.afterWork();
			// if (result1 == null) {
			// int resp = MyLog.waitHere("A causa di problemi sulla immagine,
			// \n"
			// + "viene avviato il programma p3rmn_, che \n" + "ripete il
			// controllo in maniera manuale", debug,
			// "Prosegui", "Annulla");
			// if (resp == 2)
			// IJ.runPlugIn("contMensili.p3rmn_", autoArgs);
			// }
		}
		return 0;
	}

	public ResultsTable mainMTF(String path1, String autoArgs, String info10, int mode, int timeout) {

		postmortem = path1 + "_postmortem.txt";
		pathLog = path1;
		initLog(postmortem);
		appendLog(postmortem, "**** gatherMTF *****");

		// // boolean abort = false;
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
			// autoCalled = true;
			fast = true;
			verbose = false;
			break;
		case 2:
			// questo e' il modo di funzionamento manuale
			verbose = true;
			step = false;
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

		ImagePlus imp2 = null;
		imp2 = UtilAyv.openImageNoDisplay(path1, true);
		ImagePlus imp1 = removeCalibration(imp2);
		if (verbose)
			UtilAyv.showImageMaximized(imp1);
		// int lato = 140;
		// manualSearchPosition(imp1, lato);

		String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);
		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp2, tabCodici, VERSION + "_P10__ContMensili_"
				+ MyVersion.CURRENT_VERSION + "__iw2ayv_" + MyVersionUtils.CURRENT_VERSION, autoCalled);

		if (info1 == null) {
			info1 = dummyInfo();
		}

		ResultsTable rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);

		double minSizeInPixel = 5000;
		double maxSizeInPixel = 100000;
		double minCirc = .1;
		double maxCirc = 1;
		// MyLog.waitHere("p14");
		p14rmn_ p14 = new p14rmn_();
		Roi roi4 = p14.positionSearch(imp1, minSizeInPixel, maxSizeInPixel, minCirc, maxCirc, step, fast);
		if (roi4 == null)
			return null;
		Rectangle r1 = roi4.getBounds();
		imp1.setRoi(r1);
		ImagePlus imp3 = imp1.crop();
		imp3.setTitle("cropped");
		imp3.show();

		int[][] out1 = p14rmn_.positionOptimize(imp3);

		calculateMTF(imp3, rt, out1);
		return rt;
	}

	public void calculateMTF(ImagePlus imp2, ResultsTable rt1, int[][] out1) {
		title = imp2.getTitle();
		cancel = false;
		restart = false;
		boolean done = false;
		level = 4;
		MyLog.mark("sono in calculateMTF");
		// openImage();
		imp2.show();
		Overlay over2 = new Overlay();
		imp2.setOverlay(over2);

		int width = imp2.getWidth();
		// Get the selection
		Roi roi2 = imp2.getRoi();
		String title2 = imp2.getTitle();

		if (roi2 == null) {
			// IJ.showMessageWithCancel("Warning", "All image selected");
		}
		double preciseAngle = newEdgeAngle(imp2, "VERTICAL_ANGLE");
		appendLog(postmortem, "title= " + title2 + " preciseAngle= " + preciseAngle);
		boolean completed = false;

		do {
			cancel = false;
			restart = false;

			level = level - 1;
			fixedOptions(level);
			appendLog(postmortem, "title= " + title2 + "  calculateMTF level= " + level + " sSize= " + sSize);
			MyLog.mark("level= " + level + " sSize= " + sSize);

			if (sSize < 32 && done) {
				completed = true;
				restart = false;
			}

			if (sSize < 32 && !done) {
				restart = false;
				completed = true;
				appendLog(postmortem, "uscita001 per sSize<32");
				MyLog.mark("non riesco a calcolare la MTF!!!");
				cancel = true;
				break;
			}

			int size = out1[level][0];
			int upper = out1[level][1];
			int lower = out1[level][2];
			if (upper < 0 || lower < 0) {
				appendLog(postmortem, "skippato size " + size);
				MyLog.mark("SKIPPATO sSize= " + sSize + " upper " + upper + " lower " + lower);
				restart = true;
				continue;
			}

			MyLog.mark("CALCOLO sSize= " + sSize + " lower " + lower + " upper " + upper);
			int width2 = width;
			int height2 = Math.abs(upper - lower);

			MyLog.mark("imposto la roi2 a " + 0 + " " + lower + " " + width2 + " " + height2);
			imp2.setRoi(0, lower, width, height2);
			roi2 = imp2.getRoi();
			roi2.setStrokeColor(Color.red);
			over2.add(roi2);

			appendLog(postmortem, "calculateMTF calcolo per sSize= " + sSize);

			// ESFArray contiene i valori di grigio
			generateESFArray("ESF Plot", imp2, roi2);
			if (restart) {
				MyLog.mark("restart");
				continue;
			}
			MyLog.mark("000");

			generateLSFArray("LSF Plot", ESFArray);
			calculateMax();

			ESFArrayF = alignArray(ESFArray);
			if (restart) {
				MyLog.mark("restart");
				continue;
			}

			if (cancel == false) {
				MyLog.mark("001");
				LSFArrayF = alignArray(LSFArray);
				if (restart) {
					MyLog.mark("restart");
					continue;
				}
			} else
				MyLog.mark("cancel");

			if (cancel == false) {
				MyLog.mark("002");
				ESFVector = averageVector(ESFArrayF);
			} else
				MyLog.mark("cancel");

			if (cancel == false) {
				MyLog.mark("003");
				LSFVector = averageVector(LSFArrayF);

				int aura = (LSFVector.length * 2);
				LSFDVector = new double[aura];
				int j = 0;
				int aura2 = (LSFVector.length);

				for (iii = 0; iii < (LSFDVector.length - 3); iii++) {

					if (iii % 2 == 0) {
						LSFDVector[iii] = LSFVector[j];
						j = j + 1;
					} else {
						LSFDVector[iii] = ((0.375 * LSFVector[(j - 1)]) + (0.75 * LSFVector[(j)])
								- (0.125 * LSFVector[(j + 1)]));
					}
				}
				MyLog.mark("004");

				LSFDVector[iii] = ((LSFVector[j - 1] + LSFVector[j]) * 0.5);
				LSFDVector[iii + 1] = LSFVector[j];
				LSFDVector[iii + 2] = LSFVector[j];

				int indexMax = 0;
				double valorMax = LSFDVector[0];
				for (int i = 0; i < LSFDVector.length; i++) {
					if (valorMax < LSFDVector[i]) {
						indexMax = i;
						valorMax = LSFDVector[i];
					}
				}
				MyLog.mark("005");

				iii = indexMax;
				LSFDVector[iii - 1] = ((LSFDVector[iii - 2] + LSFDVector[iii]) * 0.5);

				MTFVector = fftConversion(LSFDVector, "MTF");
				Max = obtenerMax();
				SPPVector = fftConversion(Max, "SPP");

				// -****************************************************************-S'hauria
				// d'intentar que no qued�ssin superposats, sin� en escala,
				// cada un 10 m�s avall

				if (rt1 == null) {
				} else {
					MyLog.mark("006");

					String t1 = "TESTO";
					String s2 = "VALORE";

					rt1.addValue(t1, "EDGE_ANGLE");
					rt1.addValue(s2, preciseAngle);
					rt1.incrementCounter();
					rt1.addValue(t1, "SAMPLE_SIZE");
					rt1.addValue(s2, sSize);

					for (int i1 = 0; i1 < MTFVector.length; i1++) {
						rt1.incrementCounter();
						rt1.addValue(t1, "PLOT_" + i1);
						rt1.addValue("MTF_X", i1 * (1.0 / MTFVector.length));
						rt1.addValue("MTF_Y", MTFVector[i1]);
						if (i1 < LSFVector.length) {
							rt1.addValue("LSF_X", (double) i1);
							rt1.addValue("LSF_Y", LSFVector[i1]);
						}
						if (i1 < ESFVector.length) {
							rt1.addValue("ESF_X", (double) i1);
							rt1.addValue("ESF_Y", ESFVector[i1]);
						}
						if (i1 < SPPVector.length) {
							rt1.addValue("SPP_X", (double) i1);
							rt1.addValue("SPP_Y", SPPVector[i1]);
						}
					}
				}

				generatePlot(MTFVector, "MTF");
				generatePlot(LSFVector, "LSF");
				generatePlot(ESFVector, "ESF");
				generatePlot(SPPVector, "SPP");
				rt1.show("Result");
				done = true;
				MyLog.mark("done per " + sSize);
			} else
				MyLog.mark("cancel");
		} while (!completed);
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
			myColor = Color.red;
			break;
		case 2:
			sSize = 64;
			myColor = Color.magenta;
			break;
		case 1:
			sSize = 32;
			myColor = Color.yellow;
			break;
		case 0:
			sSize = 0;
			break;
		}
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

	/***
	 * Mitja: The grey values of the line selections are tipped in a Array
	 * 
	 * Inserisce i valori dei pixel (presi come successione di linee all'interno
	 * della selezione) in un Array, NON effettua il sovracampionamento
	 * 
	 * @param title
	 * @param imp1
	 * @param roi1
	 */
	void generateESFArray(String title, ImagePlus imp1, Roi roi1) {

		Overlay over1 = imp1.getOverlay();
		if (over1 == null) {
			over1 = new Overlay();
			imp1.setOverlay(over1);
		}
		Rectangle r = roi1.getBounds();
		selecWidth = r.width;
		selecHeight = r.height;
		MyLog.initLog3(pathLog + "ESF.txt");
		MyLog.mark("selecWidth= " + selecWidth + " selecHeight= " + selecHeight);

		if (sSize >= selecWidth) {
			// MyLog.waitHere("sSize= " + sSize + " >= selecWidth= " +
			// selecWidth);
			appendLog(postmortem, "uscita002 sSize= " + sSize + " >= selecWidth= " + selecWidth);

			// IJ.showMessage("Error", "sample size is bigger than selection
			// width\nProcess canceled");
			MyLog.mark("uscita002 sSize= " + sSize + " >= selecWidth= " + selecWidth);
			restart = true;
			return;
		}

		String title1 = imp1.getTitle();

		int selectX = r.x;
		int selectY = r.y;
		int selectXFin = selectX + selecWidth;
		ESFLinea = new double[selecWidth];
		ESFArray = new double[selecHeight][selecWidth];
		appendLog(postmortem, "title= " + title1 + " sSize= " + sSize + " ----- ESFARRAY [" + ESFArray.length + "]["
				+ ESFArray[0].length + "]---------------");
		for (int i1 = 0; i1 < selecHeight; i1++) {
			// select line
			// IJ.makeLine(selectX, i1 + selectY, selectXFin - 1, i1 + selectY);
			// IJ.makeline dava problemi se il focus di ImageJ era portato su di
			// una altra immagine

			imp1.setRoi(new Line(selectX, i1 + selectY, selectXFin - 1, i1 + selectY));
			// save values
			plotESF = new ProfilePlot(imp1);
			ESFLinea = plotESF.getProfile();
			// Load Array ESF
			for (int i2 = 0; i2 < selecWidth; i2++) {
				ESFArray[i1][i2] = ESFLinea[i2];
			}
		}

		for (int i1 = 0; i1 < ESFArray.length; i1++) {
			String aux1 = "";
			for (int i2 = 0; i2 < ESFArray[0].length; i2++) {
				aux1 = aux1 + "\t" + (int) ESFArray[i1][i2];
			}
			appendLog(postmortem, aux1);
		}
		for (int i1 = 0; i1 < ESFArray.length; i1++) {
			String aux1 = "";
			for (int i2 = 0; i2 < ESFArray[0].length; i2++) {
				aux1 = aux1 + "\t" + (int) ESFArray[i1][i2];
			}
			MyLog.appendLog3(pathLog + "ESF.txt", aux1);
		}

	}

	/***
	 * Genera i valori della derivata prima
	 * 
	 * @param title
	 * @param ESFArray
	 */
	void generateLSFArray(String title, double[][] ESFArray) {

		LSFArray = new double[selecHeight][selecWidth];
		MyLog.initLog3(pathLog + "LSF.txt");

		appendLog(postmortem, "sSize= " + sSize + " ----- LSFARRAY [" + LSFArray.length + "][" + LSFArray[0].length
				+ "]---------------");

		for (kkk = 0; kkk < selecHeight; kkk++) {
			for (iii = 0; iii < selecWidth - 1; iii++) {
				LSFArray[kkk][iii] = ESFArray[kkk][iii + 1] - ESFArray[kkk][iii];
			}
		}

		for (int i1 = 0; i1 < LSFArray.length; i1++) {
			String aux1 = "";
			for (int i2 = 0; i2 < LSFArray[0].length; i2++) {
				aux1 = aux1 + " " + (int) LSFArray[i1][i2];
			}
			appendLog(postmortem, aux1);
		}

		String aux2 = "";
		for (int i1 = 0; i1 < LSFArray.length; i1++) {
			String aux1 = "";

			for (int i2 = 0; i2 < LSFArray[0].length; i2++) {
				aux2 = "" + (int) LSFArray[i1][i2];
				if ((int) LSFArray[i1][i2] < 100)
					aux2 = aux2 + " ";
				if (i2 > 0)
					aux1 = aux1 + "\t" + aux2;
				else
					aux1 = aux1 + aux2;
			}
			MyLog.appendLog3(pathLog + "LSF.txt", aux1);
		}

	}

	/***
	 * Allinea l'Array utilizzando la posizione del massimo in LSF, preleva
	 * mezzo size a sinistra e mezzo a destra
	 * 
	 * @param Array
	 * @return
	 */
	public double[][] alignArray(double[][] Array) {

		ArrayF = new double[selecHeight][sSize];
		int ini;
		int fin;

		// Create new array aligned
		for (kkk = 0; kkk < selecHeight; kkk++) {

			// Initial and end positions of k-line
			ini = (int) PosMax[kkk][2];
			fin = (int) PosMax[kkk][3];
			if (ini < 0 || fin > Array[0].length) {
				MyLog.waitHere("BBBB esce in k= " + kkk + "ini= " + ini + " fin= " + fin + " per Array.length= "
						+ Array.length);
				restart = true;
				return null;
			}

			for (iii = ini; iii < fin; iii++) {
				ArrayF[kkk][iii - ini] = Array[kkk][iii];
			}
		}
		// final array - from 32 to 512 positions
		return ArrayF;
	}

	/***
	 * 
	 * Mitja: Calculate maximum value and find 32 positions to align
	 * 
	 * Calcolo della posizione del massimo in LSFArray, per ogni linea di pixels
	 * dell'area selezionatavengono calcolati: la posizione del max (posMax)
	 * sulla linea, il valore del massimo sulla linea, le posizioni inizio e
	 * fine di un campione di pixel largo come il sample size (pixels) centrato
	 * sul posMax. I risultati vengono inseriti in un array di dimensioni
	 * [selectionHeight]x[4]
	 */
	void calculateMax() {

		PosMax = new double[selecHeight][4];
		int posMax;
		int halfSize;
		halfSize = sSize / 2;
		for (kkk = 0; kkk < selecHeight - 1; kkk++) {
			posMax = 0;
			for (iii = 0; iii < selecWidth - 1; iii++) {
				if (LSFArray[kkk][posMax] < LSFArray[kkk][iii] || LSFArray[kkk][posMax] == LSFArray[kkk][iii]) {
					posMax = iii;
				}
			}

			// The maximum value position on the line
			PosMax[kkk][0] = posMax;
			// The maximum value on the line
			PosMax[kkk][1] = LSFArray[kkk][posMax];
			// Starting and ending position to align maximum values
			PosMax[kkk][2] = PosMax[kkk][0] - halfSize;
			PosMax[kkk][3] = PosMax[kkk][0] + halfSize;
			appendLog(postmortem, "ZZZZ PosMax k: " + (int) kkk + "\t[0]:" + (int) PosMax[kkk][0] + "\t\t[1]:"
					+ (int) PosMax[kkk][1] + "\t\t[2]:" + (int) PosMax[kkk][2] + "\t\t[3]:" + (int) PosMax[kkk][3]);
		}

	}

	public double[] averageVector(double[][] Array) {

		double result;
		int j;
		double[] Vector = new double[sSize];

		// Average of all linear ESF/LSF
		for (iii = 0; iii < sSize; iii++) {
			result = 0;
			// Average of all rows i-position
			for (kkk = 0; kkk < selecHeight; kkk++) {
				result = result + Array[kkk][iii];
			}
			result = result / selecHeight;
			Vector[iii] = result;
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
		for (kkk = 0; kkk < N; kkk++) {
			Max[kkk] = PosMax[kkk][1];
		}
		return Max;
	}

	void generatePlot(double[] Vector, String plot) {

		double[] xValues;
		String ejeX = "pixel";
		String ejeY = "";
		String allTitle = "";
		// ImageProcessor imgProc;

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

		allTitle = plot + "_" + ejeY + "[" + ejeX + "]_" + title;
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
			for (iii = 1; iii < N; iii++) {
				// xValues[i]=xValues[i-1]+(0.5/(N-1));
				xValues[iii] = xValues[iii - 1] + (ny / (N - 1));
			}
		} else {
			for (iii = 0; iii < N; iii++) {
				xValues[iii] = iii + 1;
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

		for (iii = 0; iii < N; iii++) {
			// A double array is converted into a complex array ; imaginary
			// part=0
			ArrayComplex[iii] = new Complex(Vector[iii], 0);
		}
		// FFT operation
		VectorFFTC = fft(ArrayComplex);

		if (plot == "SPP") {
			// Reject the first one
			for (iii = 1; iii < M; iii++) {
				// absolute value (module)
				VectorFFTD[iii - 1] = VectorFFTC[iii].abs() / VectorFFTC[1].abs();
			}
		} else {
			for (iii = 0; iii < M; iii++) {
				// absolute value (module)
				VectorFFTD[iii] = VectorFFTC[iii].abs() / VectorFFTC[0].abs();
			}
		}

		// Normalize
		if (plot == "SPP") {
			divisor = findMaxSPP(VectorFFTD);
		} else {
			divisor = VectorFFTD[0];
		}

		for (iii = 0; iii < M; iii++) {
			VectorFFTD[iii] = VectorFFTD[iii] / divisor;
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
			double maxCirc, boolean step, boolean fast) {

		ImagePlus imp3 = removeCalibration(imp1);
		UtilAyv.showImageMaximized(imp3);
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
		if (num < 1) {
			MyLog.waitHere("IMPOSSIBILE CALCOLARE LA MTF !!!!!!!");
			return null;
		}
		Roi[] vetRoi = rm0.getRoisAsArray();
		roi0 = vetRoi[0];

		// MyLog.waitHere("" + roi0.getDebugInfo());

		imp2.setRoi(roi0);
		imp2.updateAndDraw();
		if (step)
			MyLog.waitHere("particle analyzer");

		Rectangle rect = roi0.getBounds();
		int rx = rect.x;
		int ry = rect.y;
		int w = rect.width;
		int h = rect.height;
		imp2.setRoi(rx, ry, w, h);
		if (!fast) {
			imp2.getRoi().setStrokeColor(Color.red);
			over2.addElement(imp2.getRoi());
		}
		if (step)
			MyLog.waitHere("bounding rectangle");

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

			boolean aux1 = false;
			if (i1 < aaa[0].length - 1)
				aux1 = (int) aaa[2][i1] == 0 && aaa[2][i1 + 1] > 0;
			else
				aux1 = (int) aaa[2][i1] == 0;

			if (aux1 && search) {
				search = false;
				find1x = aaa[0][i1];
				find1y = aaa[1][i1];
			}
		}
		Roi roi1 = null;
		if (!fast) {
			imp2.setRoi(new OvalRoi(find1x - 8, find1y - 8, 16, 16));

			roi1 = imp2.getRoi();
			roi1.setFillColor(Color.yellow);
			roi1.setStrokeColor(Color.yellow);
			over2.addElement(imp2.getRoi());
		}

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
			boolean aux2 = false;
			if (i1 < bbb[0].length - 1)
				aux2 = (int) bbb[2][i1] == 0 && bbb[2][i1 + 1] > 0;
			else
				aux2 = (int) bbb[2][i1] == 0;

			if (aux2 && search) {
				search = false;
				find2x = bbb[0][i1];
				find2y = bbb[1][i1];
			}
		}

		if (!fast) {
			imp2.setRoi(new OvalRoi(find2x - 8, find2y - 8, 16, 16));
			roi1 = imp2.getRoi();
			roi1.setFillColor(Color.green);
			roi1.setStrokeColor(Color.green);
			over2.addElement(imp2.getRoi());
		}
		if (step)
			MyLog.waitHere("vertici");

		double find4x = find2x;
		double find4y = 4;

		if (!fast) {
			imp2.setRoi(new OvalRoi(find4x - 8, find4y - 8, 16, 16));
			roi1 = imp2.getRoi();
			roi1.setFillColor(Color.blue);
			roi1.setStrokeColor(Color.blue);
			over2.addElement(imp2.getRoi());
		}

		double m1 = (find2y - find1y) / (find2x - find1x);
		// angolo = Math.toDegrees(Math.atan(m1)) + 90;

		// MyLog.waitHere("angolo approssimato= " + (angolo));

		double find3x = 0;
		double find3y = 0;
		find3x = (find2x - find1x) / 2 + find1x;
		find3y = (find2y - find1y) / 2 + find1y;
		if (!fast) {
			imp2.setRoi(new OvalRoi(find3x - 8, find3y - 8, 16, 16));
			roi1 = imp2.getRoi();
			roi1.setFillColor(Color.red);
			roi1.setStrokeColor(Color.red);
			over2.addElement(imp2.getRoi());
		}
		if (step)
			MyLog.waitHere("centro roi MTF");

		// MyLog.waitHere("find2x= " + find2x + " find2y= " + find2y);

		int lato = (int) (Math.abs(find1y - find2y));

		boolean trovato = false;
		do {
			int xsopra = (int) (find3x - lato / 2);
			int ysopra = (int) (find3y - lato / 2);
			int xsotto = (int) (find3x - lato / 2);
			int ysotto = (int) (find3y + lato / 2);
			double val1 = imp2.getPixel(xsopra, ysopra)[0];
			double val2 = imp2.getPixel(xsotto, ysotto)[0];
			if (val1 > 0 || val2 > 0)
				lato = lato - 1;
			else
				trovato = true;
		} while (!trovato);

		int xsel = (int) find3x - lato / 2;
		int ysel = (int) find3y - lato / 2;
		imp2.setRoi(xsel + 1, ysel, lato, lato);

		roi1 = imp2.getRoi();
		roi1.setStrokeColor(Color.green);
		over2.addElement(imp2.getRoi());
		if (step)
			MyLog.waitHere("roi MTF");
		IJ.wait(timeout);
		// ImagePlus imp3 = imp2.crop();
		// imp3.show();
		return roi1;
	}

	public static int[][] positionOptimize(ImagePlus imp1) {

		// trasformo l'immagine in b/w
		ImagePlus imp3 = applyThreshold2(imp1);
		imp3.show();

		ImagePlus imp2 = imp3.duplicate();
		imp2.setTitle("EDGE");

		// derivata
		byte def = 0;
		byte val = 0;
		// traccio solo l'edge
		ByteProcessor ip2 = (ByteProcessor) imp2.getProcessor();
		byte[] pixels2 = (byte[]) ip2.getPixels();
		// metto la posizione dell'edge in un array
		int count = 0;
		int[] vetPos = new int[imp2.getHeight()];
		for (int i1 = 0; i1 < imp2.getHeight(); i1++) {
			count = 0;
			def = (byte) (pixels2[i1 * imp1.getWidth()] & 0xFF);
			for (int i2 = 0; i2 < imp2.getWidth(); i2++) {
				val = (byte) (pixels2[i1 * imp1.getWidth() + i2] & 0xFF);
				if (val != def) {
					def = val;
					vetPos[i1] = i2;
					count++;
				}
			}
			if (count < 1)
				MyLog.waitHere("manca edge in riga " + i1);
			if (count > 1)
				MyLog.waitHere("doppio edge in riga " + i1);
		}

		int misure = 6;
		int[][] output1 = new int[misure][3];

		int size = 0;

		for (int s1 = 0; s1 < misure; s1++) {
			switch (s1) {
			case 5:
				size = 512;
				break;
			case 4:
				size = 256;
				break;
			case 3:
				size = 128;
				break;
			case 2:
				size = 64;
				break;
			case 1:
				size = 32;
				break;
			}
			// vedo dove l'edge è compatibile con l'ampiezza campione
			int upper = -1;
			int lower = -1;
			int half = size / 2;
			boolean inside = false;
			for (int i1 = 0; i1 < imp2.getHeight(); i1++) {
				if ((vetPos[i1] - half) >= 0 && (vetPos[i1] + half) < (imp2.getWidth()) && (!inside)) {
					lower = i1;
					inside = true;
				}
				if (((vetPos[i1] - half) < 0 || (vetPos[i1] + half) >= (imp2.getWidth())) && inside) {
					upper = i1;
					inside = false;
				}
			}
			output1[s1][0] = size;
			output1[s1][1] = upper;
			output1[s1][2] = lower;
			appendLog(postmortem, "positionOptimize= " + "size= " + size + " upper= " + upper + " lower= " + lower);
		}
		return output1;
	}

	public static ImagePlus applyThreshold(ImagePlus imp1) {
		int slices = 1;
		ImageProcessor ip1 = imp1.getProcessor();
		Calibration cal1 = imp1.getCalibration();

		short[] pixels1 = rawVector((short[]) ip1.getPixels(), cal1);

		int threshold = (int) cal1.getCValue(ip1.getAutoThreshold());

		ImagePlus imp2 = NewImage.createByteImage("Thresholded", imp1.getWidth(), imp1.getHeight(), slices,
				NewImage.FILL_BLACK);
		ByteProcessor ip2 = (ByteProcessor) imp2.getProcessor();
		byte[] pixels2 = (byte[]) ip2.getPixels();

		// MyLog.resultsLog(pixels1, "pixels1");
		// MyLog.waitHere("threshold= " + threshold);
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

	public static ImagePlus applyThreshold2(ImagePlus imp1) {
		int slices = 1;
		ImageProcessor ip1 = imp1.getProcessor();
		Calibration cal1 = imp1.getCalibration();

		short[] pixels1 = rawVector((short[]) ip1.getPixels(), cal1);

		int threshold = (int) cal1.getCValue(ip1.getAutoThreshold());

		ImagePlus imp2 = NewImage.createByteImage("Thresholded", imp1.getWidth(), imp1.getHeight(), slices,
				NewImage.FILL_BLACK);
		ByteProcessor ip2 = (ByteProcessor) imp2.getProcessor();
		byte[] pixels2 = (byte[]) ip2.getPixels();

		// MyLog.resultsLog(pixels1, "pixels1");
		// MyLog.waitHere("threshold= " + threshold);
		for (int i1 = 0; i1 < pixels2.length; i1++) {
			if (pixels1[i1] >= threshold) {
				pixels2[i1] = (byte) 120;
			} else {
				pixels2[i1] = (byte) 0;
			}
		}
		ip2.resetMinAndMax();
		return imp2;
	}

	public static short[] rawVector(short[] pixels1, Calibration cal1) {
		short[] out2 = new short[pixels1.length];
		for (int i1 = 0; i1 < pixels1.length; i1++) {
			out2[i1] = (short) cal1.getRawValue(pixels1[i1]);
		}
		return out2;
	}

	public static String[] dummyInfo() {
		String[] info = new String[8];

		info[0] = "HRKA_";
		info[1] = "dummy";
		info[2] = "dummy";
		info[3] = "17-gen-2017";
		info[4] = "22-feb-2017_MTF metodo SLANTED EDGE_P10__ContMensili_null__iw2ayv_null";
		info[5] = "dummy";
		info[6] = "dummy";
		info[7] = "<END>";

		return info;
	}

	public double newEdgeAngle(ImagePlus imp1, String angle) {
		ImagePlus imp2 = applyThreshold(imp1);
		ImageProcessor ip2 = imp2.getProcessor();
		// Overlay over2 = new Overlay();
		// imp2.setOverlay(over2);
		// imp2.show();
		// //
		byte[] sourcePixels = (byte[]) ip2.getPixels();
		double[] bwValues = new double[sourcePixels.length];
		for (int i = 0; i < sourcePixels.length; i++) {
			if (sourcePixels[i] == 0)
				bwValues[i] = 0;
			else
				bwValues[i] = 255.0;
		}
		ArrayList<Double> xVal = new ArrayList<Double>();
		ArrayList<Double> yVal = new ArrayList<Double>();
		// differentiation
		for (int i1 = 0; i1 < ip2.getHeight(); i1++) {
			double max = 0.0;
			double x = 0.0;
			for (int i2 = 1; i2 < ip2.getWidth(); i2++) {
				double diff = Math.abs(bwValues[i2 + i1 * ip2.getWidth()] - bwValues[(i2 + i1 * ip2.getWidth()) - 1]);
				if (diff > max) {
					max = diff;
					x = i2;
				}
			}
			if (max != 0.) {
				xVal.add((double) x);
				yVal.add((double) i1);
			}
		}
		double[] xArr = new double[xVal.size() - 1];
		double[] yArr = new double[yVal.size() - 1];
		for (int i = 0; i < xVal.size() - 1; i++) {
			xArr[i] = xVal.get(i);
			yArr[i] = yVal.get(i);
			// setOverlayPixel(over2, imp2, (int) xArr[i], (int) yArr[i],
			// Color.green, Color.red, false);
		}
		CurveFitter lineFitter = new CurveFitter(xArr, yArr);
		lineFitter.doFit(CurveFitter.STRAIGHT_LINE);
		imp2.setRoi(new Line((1 - lineFitter.getParams()[0]) / lineFitter.getParams()[1], 1,
				(ip2.getHeight() - 1 - lineFitter.getParams()[0]) / lineFitter.getParams()[1], ip2.getHeight() - 1));
		// imp2.show();
		double angleGrad = Math.toDegrees(Math.atan(1.0 / (lineFitter.getParams())[1]));
		// IJ.log("preciseEdgeAngle= " + angleGrad);
		// MyLog.waitHere("preciseEdgeAngle= " + angleGrad);
		return angleGrad;
	}

	public static void setOverlayPixel(Overlay over1, ImagePlus imp1, int x1, int y1, Color col1, Color col2,
			boolean ok) {
		imp1.setRoi(x1, y1, 1, 1);
		if (ok) {
			imp1.getRoi().setStrokeColor(col1);
			imp1.getRoi().setFillColor(col1);
		} else {
			imp1.getRoi().setStrokeColor(col1);
			imp1.getRoi().setFillColor(col2);
		}
		over1.addElement(imp1.getRoi());
		imp1.deleteRoi();
	}

	public static ImagePlus removeCalibration(ImagePlus imp1) {

		ImagePlus imp2 = NewImage.createShortImage("uncalibrated", imp1.getWidth(), imp1.getHeight(), 1,
				NewImage.FILL_BLACK);
		ImageProcessor ip2 = imp2.getProcessor();
		short[] pixels1 = UtilAyv.truePixels(imp1);
		short[] pixels2 = (short[]) ip2.getPixels();
		for (int i1 = 0; i1 < pixels1.length; i1++) {
			pixels2[i1] = pixels1[i1];
		}
		ip2.resetMinAndMax();
		imp2.updateImage();

		return imp2;
	}

	public static void appendLog(String path, String linea) {

		BufferedWriter out;
		String time = new SimpleDateFormat("yyyy-MM-dd hh:mm").format(new Date());

		try {
			out = new BufferedWriter(new FileWriter(path, true));
			out.write(time + " " + linea);
			out.newLine();
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void initLog(String path) {
		File f1 = new File(path);
		if (f1.exists()) {
			f1.delete();
		}
		appendLog(path, "---- INIZIO ---------");
	}

}
