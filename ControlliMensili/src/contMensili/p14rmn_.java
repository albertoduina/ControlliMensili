package contMensili;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.StringTokenizer;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
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
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import utils.AboutBox;
import utils.MyConst;
import utils.MyInput;
import utils.MyLine;
import utils.MyLog;
import utils.MyMsg;
import utils.MyVersionUtils;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableExpand;
import utils.TableSequence;
import utils.UtilAyv;

public class p14rmn_ implements PlugIn {

	// Frame frame;
	// ImagePlus imp, impOriginal;
	// ProfilePlot plotESF;
	// Plot plotResult;
	// int kkk;
	// int iii;
	// int selecWidth;
	// int selecHeight;
	// int sppLength;
	// double[] ESFLinea;
	// double[][] ESFArray;
	// double[][] LSFArray;
	// double[] Vector;
	// double[][] Array;
	// double[][] ArrayF;
	// double[][] ESFArrayF;
	// double[][] LSFArrayF;
	// double[][] PosMax;
	// double[] ESFVector;
	// double[] LSFVector;
	// double[] LSFDVector;
	// double[] MTFVector;
	// double[] SPPVector;
	// double[] Max;
	// String title;
	// Roi roi;
	// int optWidth;
	// int optHeight;
	// int sChannel;
	// int sFrequnits;
	// int type;
	// boolean isStack;
	// int roiType;
	// int sSize;
	boolean cancel;
	boolean restart;
	// int bit;
	// int yMax;
	// double mmSensors = 0.0;
	// int nPhotodetectors = 0;
	// double ny = 1;
	int level = 0; // deve essere globale
	static String postmortem = "";
	String pathLog = "";
	// Color myColor = Color.black;
	private static final int ABORT = 1;

	public static String VERSION = "MTF metodo SLANTED EDGE";

	private String TYPE = " >> CONTROLLO MTF SLANTED EDGE____";
	private static String fileDir = "";

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.txt
	 */

	private static int timeout = 0;
	public static final boolean forcesilent = false;

	@Override
	public void run(String args) {

		UtilAyv.setMyPrecision();

		Count c1 = new Count();
		if (!c1.jarCount("iw2ayv_")) {
			return;
		}

		String className = this.getClass().getName();
		String user1 = System.getProperty("user.name");
		TableCode tc1 = new TableCode();
		String iw2ayv1 = tc1.nameTable("codici", "csv");
		TableExpand tc2 = new TableExpand();
		String iw2ayv2 = tc1.nameTable("expand", "csv");

		VERSION = user1 + ":" + className + "build_" + MyVersion.getVersion() + ":iw2ayv_build_"
				+ MyVersionUtils.getVersion() + ":" + iw2ayv1 + ":" + iw2ayv2;

//		VERSION = className + "_build_" + MyVersion.getVersion() + "_iw2ayv_build_" + MyVersionUtils.getVersion();
		fileDir = Prefs.get("prefer.string1", "none");

		if (IJ.versionLessThan("1.43k")) {
			return;
		}

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
	 * @param preset        da utilizzare per eventuali test
	 * @param testDirectory da utilizzare per eventuali test
	 * @return
	 */

	public int manualMenu(int preset, String testDirectory) {

		boolean retry = false;
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
				retry = true;
				break;
			case 4:
				mode = 3;
			case 5:
				if (mode == 0) {
					mode = 2;
				}
				String path1 = UtilAyv.imageSelection("SELEZIONARE IMMAGINE...");
				if (path1 == null) {
					return 0;
				}
				mainMTF(path1, "0", "", mode, 0, "", null, null);
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

		String passo = TableSequence.getCode(iw2ayvTable, vetRiga[0]);

		if (nTokens == MyConst.TOKENS1) {
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			MyLog.logDebug(vetRiga[0], "P14", fileDir);

			boolean retry = false;
			int mode = 0;

			if (fast) {
				retry = false;
				mode = 1;
				mainMTF(path1, autoArgs, info10, mode, timeout, passo, vetRiga, iw2ayvTable);
				UtilAyv.afterWork();
			} else {
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
						mainMTF(path1, autoArgs, info10, mode, timeout, passo, vetRiga, iw2ayvTable);
						UtilAyv.afterWork();
						break;
					}
				} while (retry);
			}
			new AboutBox().close();
			UtilAyv.afterWork();
		}
		return 0;
	}

	public void mainMTF(String path1, String autoArgs, String info10, int mode, int timeout, String passo,
			int[] vetRiga, String[][] iw2ayvTable) {

		postmortem = path1 + "_postmortem.txt";
		pathLog = path1;
		MyLog.initLog(postmortem);
		MyLog.appendLog(postmortem, "**** mainMTF *****");

		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean fast = false;
		boolean silent = false;

		if (forcesilent) {
			mode = 0;
		}

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
			// test = true;
			break;
		}

		ImagePlus imp2 = null;
		imp2 = UtilAyv.openImageNoDisplay(path1, true);
		String title2 = imp2.getTitle();
		ImagePlus imp1 = removeCalibration(imp2);
		if (verbose)
		 {
			UtilAyv.showImageMaximized(imp1);
		// int lato = 140;
		// manualSearchPosition(imp1, lato);
		}

		// String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);
		TableCode tc1 = new TableCode();
		String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");

		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp2, tabCodici, VERSION + "_P14__ContMensili_"
				+ MyVersion.CURRENT_VERSION + "__iw2ayv_" + MyVersionUtils.CURRENT_VERSION, autoCalled);

		if (info1 == null) {
			info1 = dummyInfo();
		}
		info1[0] = passo;

		double minSizeInPixel = 5000;
		double maxSizeInPixel = 100000;
		double minCirc = .1;
		double maxCirc = 1;
		// MyLog.waitHere("p14");
		p14rmn_ p14 = new p14rmn_();
		Roi roi4 = p14.positionSearch(imp1, minSizeInPixel, maxSizeInPixel, minCirc, maxCirc, step, fast);
		if (roi4 == null) {
			return;
		}
		Rectangle r1 = roi4.getBounds();
		imp1.setRoi(r1);
		level = 3;
		int sSize = 0;
		do {
			ImagePlus imp3 = imp1.crop();
			imp3.setTitle("cropped");
			imp3.show();

			cancel = false;
			restart = false;
			sSize = 0;
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
			MyLog.appendLog(postmortem, "title= " + title2 + "  calculateMTF level= " + level + " sSize= " + sSize);
			ResultsTable rt = calculateMTF(imp3, info1, sSize);
			rt.showRowNumbers(true);

			if (!(rt == null)) {
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);
			}
			level = level - 1;
			MyLog.waitHere("FINITO ?");
			UtilAyv.afterWork();
		} while (level > 0);
		return;
	}

	public ResultsTable calculateMTF(ImagePlus imp1, String[] info, int sSize) {
		String title = imp1.getTitle();
		ImagePlus imp2 = imp1.crop();
		cancel = false;
		restart = false;
		// MyLog.mark("sono in calculateMTF");
		imp2.show();
		Overlay over2 = new Overlay();
		imp2.setOverlay(over2);

		int width = imp2.getWidth();
		int height = imp2.getHeight();
		// Get the selection
		Roi roi2 = imp2.getRoi();
		ImageStatistics stat2 = null;

		String title2 = imp2.getTitle();

		if (roi2 == null) {
			// IJ.showMessageWithCancel("Warning", "All image selected");
		}
		double preciseAngle = newEdgeAngle(imp2, "VERTICAL_ANGLE");
		MyLog.appendLog(postmortem, "title= " + title2 + " preciseAngle= " + preciseAngle);
		boolean completed = false;
		ResultsTable rt1 = null;

		int height2 = height;
		imp2.setRoi(0, 0, width, height2);
		roi2 = imp2.getRoi();
		stat2 = imp2.getStatistics();
		if (roi2 == null) {
			MyLog.waitHere("roi2==null");
		}

		roi2.setStrokeColor(Color.red);
		over2.add(roi2);

		Rectangle r2 = roi2.getBounds();
		int selecHeight = r2.height;
		MyLog.appendLog(postmortem, "calculateMTF calcolo per sSize= " + sSize);
		// ESFArray contiene i valori di grigio
		double[][] ESFArray = generateESFArray("ESF Plot", imp2, roi2, sSize);
		if (restart) {
			MyLog.appendLog(postmortem, "esce per sSize= " + sSize);
			return null;
		}

		double[][] LSFArray = generateLSFArray("LSF Plot", ESFArray, roi2);
		double[][] PosMax = calculateMax(LSFArray, roi2, sSize);

		MyLog.appendLog(postmortem, "eseguo alignArray per ESFArray");
		double[][] ESFArrayF = alignArray(ESFArray, PosMax, roi2, sSize);
		if (restart) {
			MyLog.appendLog(postmortem, "esce per sSize= " + sSize);
			return null;
		}

		MyLog.appendLog(postmortem, "eseguo alignArray per LSFArray");
		double[][] LSFArrayF = alignArray(LSFArray, PosMax, roi2, sSize);
		if (restart) {
			MyLog.appendLog(postmortem, "restart per sSize= " + sSize);
			return null;
		}

		double[] ESFVector = averageVector(ESFArrayF, roi2, sSize);

		double[] LSFVector = averageVector(LSFArrayF, roi2, sSize);

		int aura = (LSFVector.length * 2);
		double[] LSFDVector = new double[aura];
		int j1 = 0;
		int aura2 = (LSFVector.length);
		int i2 = 0;

		for (int i1 = 0; i1 < (LSFDVector.length - 3); i1++) {

			if (i1 % 2 == 0) {
				LSFDVector[i1] = LSFVector[j1];
				j1 = j1 + 1;
			} else {
				LSFDVector[i1] = ((0.375 * LSFVector[(j1 - 1)]) + (0.75 * LSFVector[(j1)])
						- (0.125 * LSFVector[(j1 + 1)]));
			}
			i2 = i1;
		}
		MyLog.appendLog(postmortem, "passato per sSize= " + sSize);

		LSFDVector[i2] = ((LSFVector[j1 - 1] + LSFVector[j1]) * 0.5);
		LSFDVector[i2 + 1] = LSFVector[j1];
		LSFDVector[i2 + 2] = LSFVector[j1];

		int indexMax = 0;
		double valorMax = LSFDVector[0];
		for (int i = 0; i < LSFDVector.length; i++) {
			if (valorMax < LSFDVector[i]) {
				indexMax = i;
				valorMax = LSFDVector[i];
			}
		}

		i2 = indexMax;
		LSFDVector[i2 - 1] = ((LSFDVector[i2 - 2] + LSFDVector[i2]) * 0.5);
		MyLog.appendLog(postmortem, "fft ready per sSize= " + sSize);

		double[] MTFVector = fftConversion(LSFDVector, "MTF");
		double[] Max = obtenerMax(selecHeight, PosMax);
		double[] SPPVector = fftConversion(Max, "SPP");

		// -****************************************************************-S'hauria
		// d'intentar que no qued�ssin superposats, sin� en escala,
		// cada un 10 m�s avall

		if (sSize == 32) {
			// MyLog.waitHere("info[0]= " + info[0]);
			// MyLog.waitHere();
			String s1 = info[0];
			StringBuffer sb1 = new StringBuffer(s1);
			sb1.setCharAt(2, '#');
			s1 = sb1.toString();
			info[0] = s1;
			// MyLog.waitHere("info= " + info);
			// MyLog.waitHere();
		}

		MyLog.appendLog(postmortem, "results per sSize= " + sSize);

		rt1 = ReportStandardInfo.putSimpleStandardInfoRT_new(info);

		if (rt1 == null) {
			MyLog.waitHere("rt1==null");
		} else {

			String t1 = "TESTO";
			String s2 = "VALORE";
			String s3 = "MTF_X";
			String s4 = "MTF_Y";
			String s5 = "LSF_X";
			String s6 = "LSF_Y";
			String s7 = "ESF_X";
			String s8 = "ESF_Y";
			String s9 = "SPP_X";
			String s10 = "SPP_Y";
			String s11 = "roi_x";
			String s12 = "roi_y";
			String s13 = "roi_b";
			String s14 = "roi_h";

			rt1.addValue(t1, "EDGE_ANGLE");
			rt1.addValue(s2, preciseAngle);

			rt1.incrementCounter();
			rt1.addValue(t1, "SAMPLE_SIZE");
			rt1.addValue(s2, sSize);

			for (int i1 = 0; i1 < MTFVector.length; i1++) {
				rt1.incrementCounter();
				rt1.addValue(t1, "PLOT_" + i1);
				rt1.addValue(s3, i1 * (1.0 / MTFVector.length));
				rt1.addValue(s4, MTFVector[i1]);
				if (i1 < LSFVector.length) {
					rt1.addValue(s5, i1);
					rt1.addValue(s6, LSFVector[i1]);
				}
				if (i1 < ESFVector.length) {
					rt1.addValue(s7, i1);
					rt1.addValue(s8, ESFVector[i1]);
				}
				if (i1 < SPPVector.length) {
					rt1.addValue(s9, i1);
					rt1.addValue(s10, SPPVector[i1]);
				}
				rt1.addValue(s11, stat2.roiX);
				rt1.addValue(s12, stat2.roiY);
				rt1.addValue(s13, stat2.roiWidth);
				rt1.addValue(s14, stat2.roiHeight);
			}

		}

		MyLog.appendLog(postmortem, "grafic per sSize= " + sSize);

		int sFrequnits = 1;
		MyLog.waitHere("SIZE= " + sSize + " MTF= " + MTFVector.length + " LSF= " + LSFVector.length + " ESF= "
				+ ESFVector.length + " SPP= " + SPPVector.length);

		generatePlot(MTFVector, "MTF", title, sFrequnits);
		generatePlot(LSFVector, "LSF", title, sFrequnits);
		generatePlot(ESFVector, "ESF", title, sFrequnits);
		generatePlot(SPPVector, "SPP", title, sFrequnits);
		rt1.show("Result");
		MyLog.appendLog(postmortem, "done per sSize= " + sSize);
		return rt1;
	}

	public static void manualSearchPosition(ImagePlus imp1, int lato) {
		imp1.setRoi((imp1.getWidth() - lato) / 2, (imp1.getHeight() - lato) / 2, lato, lato);
		MyLog.waitHere("posizionare la ROI sullo SLANTED EDGE, in modo che la parte nera sia A SINISTRA e premere OK");
	}

	/***
	 * Mitja: The grey values of the line selections are tipped in a Array
	 *
	 * Inserisce i valori dei pixel (presi come successione di linee all'interno
	 * della selezione) in un Array, NON effettua il sovracampionamento?
	 *
	 * @param title
	 * @param imp1
	 * @param roi1
	 */
	double[][] generateESFArray(String title, ImagePlus imp1, Roi roi1, int sSize) {

		Overlay over1 = imp1.getOverlay();
		if (over1 == null) {
			over1 = new Overlay();
			imp1.setOverlay(over1);
		}
		Rectangle r = roi1.getBounds();
		int selecWidth = r.width;
		int selecHeight = r.height;
		MyLog.initLog3(pathLog + "ESF.txt");

		if (sSize >= selecWidth) {
			MyLog.appendLog(postmortem, "uscita sSize= " + sSize + " >= selecWidth= " + selecWidth);
			// IJ.showMessage("Error", "sample size is bigger than selection
			restart = true;
			return null;
		}

		String title1 = imp1.getTitle();

		int selectX = r.x;
		int selectY = r.y;
		int selectXFin = selectX + selecWidth;
		double[] ESFLinea = new double[selecWidth];
		double[][] ESFArray = new double[selecHeight][selecWidth];
		MyLog.appendLog(postmortem, "title= " + title1 + " sSize= " + sSize + " ----- ESFARRAY [" + ESFArray.length
				+ "][" + ESFArray[0].length + "]---------------");
		for (int i1 = 0; i1 < selecHeight; i1++) {
			// select line
			// IJ.makeLine(selectX, i1 + selectY, selectXFin - 1, i1 + selectY);
			// IJ.makeline dava problemi se il focus di ImageJ era portato su di
			// una altra immagine
			imp1.setRoi(new Line(selectX, i1 + selectY, selectXFin - 1, i1 + selectY));
			// save values
			ProfilePlot plotESF = new ProfilePlot(imp1);
			ESFLinea = plotESF.getProfile();
			// Load Array ESF
			for (int i2 = 0; i2 < selecWidth; i2++) {
				ESFArray[i1][i2] = ESFLinea[i2];
			}
		}

		for (double[] element : ESFArray) {
			String aux1 = "";
			for (int i2 = 0; i2 < ESFArray[0].length; i2++) {
				aux1 = aux1 + "\t" + (int) element[i2];
			}
			MyLog.appendLog(postmortem, aux1);
		}
		for (double[] element : ESFArray) {
			String aux1 = "";
			for (int i2 = 0; i2 < ESFArray[0].length; i2++) {
				aux1 = aux1 + "\t" + (int) element[i2];
			}
			MyLog.appendLog3(pathLog + "ESF.txt", aux1);
		}
		return ESFArray;
	}

	/***
	 * Genera i valori della derivata prima
	 *
	 * @param title
	 * @param ESFArray
	 */
	double[][] generateLSFArray(String title, double[][] ESFArray, Roi roi1) {

		Rectangle r = roi1.getBounds();
		int selecWidth = r.width;
		int selecHeight = r.height;

		double[][] LSFArray = new double[selecHeight][selecWidth];
		MyLog.initLog3(pathLog + "LSF.txt");

		MyLog.appendLog(postmortem,
				" ----- LSFARRAY [" + LSFArray.length + "][" + LSFArray[0].length + "]---------------");

		for (int i1 = 0; i1 < selecHeight; i1++) {
			for (int i2 = 0; i2 < selecWidth - 1; i2++) {
				LSFArray[i1][i2] = ESFArray[i1][i2 + 1] - ESFArray[i1][i2];
			}
		}

		for (double[] element : LSFArray) {
			String aux1 = "";
			for (int i2 = 0; i2 < LSFArray[0].length; i2++) {
				aux1 = aux1 + " " + (int) element[i2];
			}
			MyLog.appendLog(postmortem, aux1);
		}

		String aux2 = "";
		for (double[] element : LSFArray) {
			String aux1 = "";

			for (int i2 = 0; i2 < LSFArray[0].length; i2++) {
				aux2 = "" + (int) element[i2];
				if ((int) element[i2] < 100) {
					aux2 = aux2 + " ";
				}
				if (i2 > 0) {
					aux1 = aux1 + "\t" + aux2;
				} else {
					aux1 = aux1 + aux2;
				}
			}
			MyLog.appendLog3(pathLog + "LSF.txt", aux1);
		}
		return LSFArray;
	}

	/***
	 * Allinea l'Array utilizzando la posizione del massimo in LSF, preleva mezzo
	 * size a sinistra e mezzo a destra
	 *
	 * @param Array
	 * @return
	 */
	public double[][] alignArrayOLD(double[][] Array, double[][] PosMax, Roi roi1, int sSize) {

		Rectangle r = roi1.getBounds();
		int selecWidth = r.width;
		int selecHeight = r.height;

		double[][] ArrayF = new double[selecHeight][sSize];
		MyLog.appendLog(postmortem, "selecHeight= " + selecHeight);
		int ini;
		int fin;

		// Create new array aligned
		for (int i1 = 0; i1 < selecHeight; i1++) {

			// Initial and end positions of k-line
			ini = (int) PosMax[i1][2];
			fin = (int) PosMax[i1][3];

			MyLog.appendLog(postmortem, "i1= " + i1 + " ini= " + ini + " fin= " + fin);
			if (ini < 0 || fin > Array[0].length) {
				MyLog.appendLog(postmortem, "restart ed esce i1= " + i1 + " ini= " + ini + " fin= " + fin);
				restart = true;
				return null;
			}

			for (int i2 = ini; i2 < fin; i2++) {
				ArrayF[i1][i2 - ini] = Array[i1][i2];
			}
		}
		// final array - from 32 to 512 positions
		return ArrayF;
	}

	/***
	 * Allinea l'Array utilizzando la posizione del massimo in LSF, preleva mezzo
	 * size a sinistra e mezzo a destra, gestisce anche le linee non allineabili
	 *
	 * @param Array
	 * @return
	 */
	public double[][] alignArray(double[][] Array, double[][] PosMax, Roi roi1, int sSize) {

		Rectangle r = roi1.getBounds();
		// int selecWidth = r.width;
		int selecHeight = r.height;

		MyLog.appendLog(postmortem, "selecHeight= " + selecHeight);
		int ini;
		int fin;

		// prescan per vedere che linee possono essere allineate
		int primabuona = -1;
		int ultimabuona = -1;
		int hSize = -1;
		boolean dentro = false;
		for (int i1 = 0; i1 < selecHeight - 1; i1++) {
			ini = (int) PosMax[i1][2];
			fin = (int) PosMax[i1][3];

			if (ini >= 0 && fin < Array[0].length) {
				if (!dentro) {
					primabuona = i1;
					dentro = true;
				} else {
					ultimabuona = i1;
				}
			}
			MyLog.appendLog(postmortem, "alignArray prescan i1= " + i1 + " ini= " + ini + " fin= " + fin + " sSize= "
					+ sSize + " primabuona= " + primabuona + " ultimabuona= " + ultimabuona);

		}
		ultimabuona = ultimabuona - 2;
		hSize = ultimabuona - primabuona;
		MyLog.appendLog(postmortem, "primabuona= " + primabuona + " ultimabuona= " + ultimabuona + " sSize= " + sSize);

		if (hSize <= sSize || primabuona < 0 || ultimabuona < 0) {
			MyLog.waitHere("primabuona= " + primabuona + " ultimabuona= " + ultimabuona + " hSize= " + hSize);
			MyLog.appendLog(postmortem, "restart ed esce per hSize= " + hSize + " sSize= " + sSize);
			restart = true;
			return null;
		}

		double[][] ArrayF = new double[ultimabuona - primabuona][sSize];

		// Create new array aligned
		for (int i1 = primabuona; i1 < ultimabuona; i1++) {

			// Initial and end positions of k-line
			ini = (int) PosMax[i1][2];
			fin = (int) PosMax[i1][3];

			MyLog.appendLog(postmortem,
					"alignArray allinea i1= " + i1 + " ini= " + ini + " fin= " + fin + " sSize= " + sSize);

			if (ini < 0 || fin > Array[0].length) {
				MyLog.appendLog(postmortem,
						"GRAVE alignArray allinea restart ed esce i1= " + i1 + " ini= " + ini + " fin= " + fin);
				restart = true;
				return null;
			}

			int count = 0;
			for (int i2 = ini; i2 < fin; i2++) {
				ArrayF[count++][i2 - ini] = Array[i1][i2];
			}

		}

		MyLog.appendLog(postmortem, "------ alignArray allineato -----");
		for (double[] element : Array) {
			String aux1 = "";
			for (int i2 = 0; i2 < Array[0].length; i2++) {
				aux1 = aux1 + "\t" + (int) element[i2];
			}
			MyLog.appendLog(postmortem, aux1);
		}
		// final array - from 32 to 512 positions
		return ArrayF;
	}

	/***
	 *
	 * Mitja: Calculate maximum value and find 32 positions to align
	 *
	 * Calcolo della posizione del massimo in LSFArray, per ogni linea di pixels
	 * dell'area selezionatavengono calcolati: la posizione del max (posMax) sulla
	 * linea, il valore del massimo sulla linea, le posizioni inizio e fine di un
	 * campione di pixel largo come il sample size (pixels) centrato sul posMax. I
	 * risultati vengono inseriti in un array di dimensioni [selectionHeight]x[4]
	 */
	double[][] calculateMax(double[][] LSFArray, Roi roi1, int sSize) {
		Rectangle r = roi1.getBounds();
		int selecWidth = r.width;
		int selecHeight = r.height;

		double[][] PosMax = new double[selecHeight][4];
		int posMax;
		int halfSize;
		halfSize = sSize / 2;
		for (int i1 = 0; i1 < selecHeight - 1; i1++) {
			posMax = 0;
			for (int i2 = 0; i2 < selecWidth - 1; i2++) {
				if (LSFArray[i1][posMax] < LSFArray[i1][i2] || LSFArray[i1][posMax] == LSFArray[i1][i2]) {
					posMax = i2;
				}
			}

			// The maximum value position on the line
			PosMax[i1][0] = posMax;
			// The maximum value on the line
			PosMax[i1][1] = LSFArray[i1][posMax];
			// Starting and ending position to align maximum values
			PosMax[i1][2] = PosMax[i1][0] - halfSize;
			PosMax[i1][3] = PosMax[i1][0] + halfSize;
			MyLog.appendLog(postmortem, "ZZZZ PosMax k: " + i1 + "\t[0]:" + (int) PosMax[i1][0] + "\t\t[1]:"
					+ (int) PosMax[i1][1] + "\t\t[2]:" + (int) PosMax[i1][2] + "\t\t[3]:" + (int) PosMax[i1][3]);
		}
		return PosMax;
	}

	public double[] averageVector(double[][] Array, Roi roi1, int sSize) {
		// Rectangle r = roi1.getBounds();
		// int selecWidth = r.width;
		if (Array == null) {
			MyLog.waitHere("Array==null");
			return null;
		}
		int selecHeight = Array.length;
		// MyLog.waitHere("selecHeight =" + selecHeight);

		double result;
		// int j;
		double[] Vector = new double[sSize];

		// Average of all linear ESF/LSF
		for (int i2 = 0; i2 < sSize; i2++) {
			result = 0;
			// Average of all rows i-position
			for (int i1 = 0; i1 < selecHeight; i1++) {
				result = result + Array[i1][i2];
			}
			result = result / selecHeight;
			Vector[i2] = result;
		}
		return Vector;
	}

	/// TITOLO: FOLLIE DI IW2AYV
	public double[] rebellottationOfVector(double[][] Array, Roi roi1, int sSize, double angle, double pixelPitch) {
		// Rectangle r = roi1.getBounds();
		// int selecWidth = r.width;
		angle = 44.0;
		pixelPitch = 1;
		double spacing = pixelPitch * Math.sin(angle);
		double count = pixelPitch / spacing;
		MyLog.waitHere("count= " + count);
		if (Array == null) {
			MyLog.waitHere("Array==null");
			return null;
		}
		int selecHeight = Array.length;
		// MyLog.waitHere("selecHeight =" + selecHeight);

		double result;
		// int j;
		double[] Vector = new double[sSize];

		// Average of all linear ESF/LSF
		for (int i2 = 0; i2 < sSize; i2++) {
			result = 0;
			// Average of all rows i-position
			for (int i1 = 0; i1 < selecHeight; i1++) {
				result = result + Array[i1][i2];
			}
			result = result / selecHeight;
			Vector[i2] = result;
		}
		return Vector;
	}

	public int longPotDos(int length) {
		int N;
		double log = Math.log(length) / Math.log(2);
		N = (int) Math.pow(2, (int) Math.floor(log));
		return N;
	}

	public double[] obtenerMax(int selecHeight, double[][] PosMax) {
		int N = longPotDos(selecHeight);
		double[] Max = new double[N];
		for (int i1 = 0; i1 < N; i1++) {
			Max[i1] = PosMax[i1][1];
		}
		return Max;
	}

	void generatePlot(double[] Vector, String plot, String title, int sFrequnits) {

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
		Plot plotResult = new Plot(allTitle, ejeX, ejeY, xValues, Vector);

		double yMax = 0;
		// plot limits
		if (plot == "ESF") {
			plotResult.setLimits(1, Vector.length, 0, yMax);
		}

		if (plot == "LSF") {
			plotResult.setLimits(1, Vector.length, 0, yMax);
		}

		if (plot == "MTF") {
			plotResult.setLimits(0, 1, 0, 1);
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
			for (int i2 = 1; i2 < N; i2++) {
				// xValues[i]=xValues[i-1]+(0.5/(N-1));
				xValues[i2] = xValues[i2 - 1] + (1 / ((double) N - 1));
			}
			// MyLog.logVector(xValues, "xValues");
		} else {
			for (int i2 = 0; i2 < N; i2++) {
				xValues[i2] = i2 + 1;
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

		for (int i2 = 0; i2 < N; i2++) {
			// A double array is converted into a complex array ; imaginary
			// part=0
			ArrayComplex[i2] = new Complex(Vector[i2], 0);
		}
		// FFT operation
		VectorFFTC = fft(ArrayComplex);

		if (plot == "SPP") {
			// Reject the first one
			for (int i2 = 1; i2 < M; i2++) {
				// absolute value (module)
				VectorFFTD[i2 - 1] = VectorFFTC[i2].abs() / VectorFFTC[1].abs();
			}
		} else {
			for (int i2 = 0; i2 < M; i2++) {
				// absolute value (module)
				VectorFFTD[i2] = VectorFFTC[i2].abs() / VectorFFTC[0].abs();
			}
		}
		// Normalize
		if (plot == "SPP") {
			divisor = findMaxSPP(VectorFFTD);
		} else {
			divisor = VectorFFTD[0];
		}

		for (int i2 = 0; i2 < M; i2++) {
			VectorFFTD[i2] = VectorFFTD[i2] / divisor;
		}
		return VectorFFTD;
	}

	public double findMaxSPP(double[] Vector) {
		double max = 0;
		double value;
		for (double element : Vector) {
			if (element > max) {
				max = element;
			}
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
		@Override
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
		if (N % 2 != 0) {
			throw new RuntimeException("N is not a power of 2");
		}
		Complex[] even = new Complex[N / 2];
		Complex[] odd = new Complex[N / 2];
		for (int k = 0; k < N / 2; k++) {
			even[k] = x[2 * k];
		}
		for (int k = 0; k < N / 2; k++) {
			odd[k] = x[2 * k + 1];
		}

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
		if (excludeEdges) {
			options = ParticleAnalyzer.SHOW_OUTLINES + ParticleAnalyzer.ADD_TO_MANAGER
					+ ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
		}

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
		if (step) {
			MyLog.waitHere("particle analyzer");
		}

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
		if (step) {
			MyLog.waitHere("bounding rectangle");
		}

		// imp2.setRoi(new Line(rx, ry, rx, ry + h));
		imp2.setRoi(new Line(rx + w - 1, ry + h, rx + w - 1, ry));
		double[][] aaa = MyLine.decomposer(imp2);
		// MyLog.waitHere();
		boolean search = true;
		double find1x = 0;
		double find1y = 0;
		for (int i1 = 0; i1 < aaa[0].length; i1++) {
			boolean aux1 = false;
			if (i1 < aaa[0].length - 1) {
				aux1 = (int) aaa[2][i1] == 0 && aaa[2][i1 + 1] > 0;
			} else {
				aux1 = (int) aaa[2][i1] == 0;
			}

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
		search = true;
		double find2x = 0;
		double find2y = 0;
		imp2.setRoi(new Line(rx + w - 1, ry + h - 1, rx, ry + h - 1));
		double[][] bbb = MyLine.decomposer(imp2);

		for (int i1 = 0; i1 < bbb[0].length; i1++) {
			boolean aux2 = false;
			if (i1 < bbb[0].length - 1) {
				aux2 = (int) bbb[2][i1] == 0 && bbb[2][i1 + 1] > 0;
			} else {
				aux2 = (int) bbb[2][i1] == 0;
			}

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
		if (step) {
			MyLog.waitHere("vertici");
		}

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
		if (step) {
			MyLog.waitHere("centro roi MTF");
		}
		int lato = (int) (Math.abs(find1y - find2y));

		boolean trovato = false;
		do {
			int xsopra = (int) (find3x - lato / 2);
			int ysopra = (int) (find3y - lato / 2);
			int xsotto = (int) (find3x - lato / 2);
			int ysotto = (int) (find3y + lato / 2);
			double val1 = imp2.getPixel(xsopra, ysopra)[0];
			double val2 = imp2.getPixel(xsotto, ysotto)[0];
			if (val1 > 0 || val2 > 0) {
				lato = lato - 1;
			} else {
				trovato = true;
			}
		} while (!trovato);

		int xsel = (int) find3x - lato / 2;
		int ysel = (int) find3y - lato / 2;
		imp2.setRoi(xsel + 1, ysel, lato, lato);

		roi1 = imp2.getRoi();
		roi1.setStrokeColor(Color.green);
		over2.addElement(imp2.getRoi());
		if (step) {
			MyLog.waitHere("roi MTF");
		}
		IJ.wait(timeout);
		return roi1;
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
			if (sourcePixels[i] == 0) {
				bwValues[i] = 0;
			} else {
				bwValues[i] = 255.0;
			}
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
				xVal.add(x);
				yVal.add((double) i1);
			}
		}
		double[] xArr = new double[xVal.size() - 1];
		double[] yArr = new double[yVal.size() - 1];
		for (int i = 0; i < xVal.size() - 1; i++) {
			xArr[i] = xVal.get(i);
			yArr[i] = yVal.get(i);
		}
		CurveFitter lineFitter = new CurveFitter(xArr, yArr);
		lineFitter.doFit(CurveFitter.STRAIGHT_LINE);
		imp2.setRoi(new Line((1 - lineFitter.getParams()[0]) / lineFitter.getParams()[1], 1,
				(ip2.getHeight() - 1 - lineFitter.getParams()[0]) / lineFitter.getParams()[1], ip2.getHeight() - 1));
		double angleGrad = Math.toDegrees(Math.atan(1.0 / (lineFitter.getParams())[1]));
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

}
