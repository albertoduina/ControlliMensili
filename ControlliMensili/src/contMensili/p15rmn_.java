package contMensili;

/***
*
* Copied from https://github.com/oskrebkova/mtf_analyzer
* 
* Authors: Olga Skrebkova   (not sure, lack author informations)
* 
* Adapted by Alberto Duina  01/02/2017
* 
*************************************************************************/

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.regex.Pattern;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Line;
import ij.gui.NewImage;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.ProfilePlot;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.CurveFitter;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import utils.AboutBox;
import utils.MyConst;
import utils.MyInput;
import utils.MyLine;
import utils.MyLog;
import utils.MyMsg;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableSequence;
import utils.UtilAyv;

public class p15rmn_ implements PlugIn {

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
	// public static double angolo = 0;
	String auxPath1 = null;

	private static final int ABORT = 1;

	public static String VERSION = "MTF metodo SLANTED EDGE";

	private String TYPE = " >> CONTROLLO MTF SLANTED EDGE____";
	private static String fileDir = "";

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.txt
	 */

	public static String auxpath = "";
	private static final boolean debug = true;

	private static int timeout = 0;
	public static final boolean forcesilent = false;

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
				new AboutBox().credits(titolo, "CREDITS to: OSrebkova", "no mail known", MyVersion.CURRENT_VERSION);

				retry = true;
				break;
			case 3:
				timeout = MyInput.myIntegerInput("Ritardo avanzamento (0 = infinito)", "      [msec]", 1000, 0);
				retry = true;

				break;
			case 4:
				// step = true;
				mode = 3;
			case 5:
				if (mode == 0)
					mode = 2;
				String path1 = UtilAyv.imageSelection("SELEZIONARE IMMAGINE...");
				auxPath1 = path1;
				if (path1 == null)
					return 0;
				mainMTF(path1, "0", "", mode, 0, "");
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
			// UtilAyv.checkImages(vetRiga, iw2ayvTable, 2, debug);
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			MyLog.logDebug(vetRiga[0], "P14", fileDir);

			boolean retry = false;
			int mode = 0;

			if (fast) {
				retry = false;
				mode = 1;
				result1 = mainMTF(path1, autoArgs, info10, mode, timeout, passo);

				if (!(result1 == null))
					UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, result1);

				UtilAyv.afterWork();

			} else
				do {
					int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
							TableSequence.getCode(iw2ayvTable, vetRiga[0]),
							TableSequence.getCoil(iw2ayvTable, vetRiga[0]), vetRiga[0] + 1,
							TableSequence.getLength(iw2ayvTable));
					MyLog.waitHere("AUTO userSelection1=" + userSelection1);

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
						result1 = mainMTF(path1, autoArgs, info10, mode, timeout, "");

						if (result1 == null) {
							break;
						}

						UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, result1);

						UtilAyv.afterWork();
						break;
					}
				} while (retry);
			new AboutBox().close();
		}
		return 0;
	}

	public ResultsTable mainMTF(String path1, String autoArgs, String info10, int mode, int timeout, String passo) {

		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
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
			break;
		}

		ImagePlus imp1 = null;
		if (verbose)
			imp1 = UtilAyv.openImageMaximized(path1);
		else
			imp1 = UtilAyv.openImageNoDisplay(path1, true);

		// int lato = 140;
		// manualSearchPosition(imp1, lato);

	//	String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);
		TableCode tc1= new TableCode();
		String[][] tabCodici = tc1.loadMultipleTable( "codici", ".csv");

		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp1, tabCodici, VERSION + "_P10__ContMensili_"
				+ MyVersion.CURRENT_VERSION + "__iw2ayv_" + MyVersionUtils.CURRENT_VERSION, autoCalled);

		if (info1 == null) {
			info1 = dummyInfo();
		}

		info1[0] = passo;

		ResultsTable rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);
		rt.showRowNumbers(true);


		double minSizeInPixel = 5000;
		double maxSizeInPixel = 100000;
		double minCirc = .1;
		double maxCirc = 1;
		// MyLog.waitHere("p15");
		p15rmn_ p15 = new p15rmn_();
		Roi roi4 = p15.positionSearch(imp1, minSizeInPixel, maxSizeInPixel, minCirc, maxCirc, step, fast);
		if (roi4 == null)
			return null;
		Rectangle r1 = roi4.getBounds();
		imp1.setRoi(r1);
		ImagePlus imp3 = imp1.crop();
		imp3.show();

		String imp3Name = path1 + "imp3_p15.bmp";
		new FileSaver(imp3).saveAsBmp(imp3Name);

		double quotient = 1.0;
		gatherMTF(imp3, "VERTICAL_ANGLE", quotient, rt);
		return rt;
	}

	public static void manualSearchPosition(ImagePlus imp1, int lato) {
		imp1.setRoi((imp1.getWidth() - lato) / 2, (imp1.getHeight() - lato) / 2, lato, lato);
		MyLog.waitHere("posizionare la ROI sullo SLANTED EDGE, in modo che la parte nera sia A SINISTRA e premere OK");
	}

	public Roi positionSearch(ImagePlus imp1, double minSizeInPixel, double maxSizeInPixel, double minCirc,
			double maxCirc, boolean step, boolean fast) {

		ImagePlus imp3 = removeCalibration(imp1);
		UtilAyv.showImageMaximized(imp3);
		ImagePlus imp2 = applyThreshold(imp1);
		UtilAyv.showImageMaximized(imp2);

		// ImagePlus imp2 = applyThreshold(imp1);
		// UtilAyv.showImageMaximized(imp2);

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
		imp2.setRoi(new Line(rx + w - 1, ry + h, rx + w - 1, ry));
		double[][] aaa = MyLine.decomposer(imp2);
		boolean search = true;
		double find1x = 0;
		double find1y = 0;
		for (int i1 = 0; i1 < aaa[0].length; i1++) {
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

		search = true;
		double find2x = 0;
		double find2y = 0;
		imp2.setRoi(new Line(rx + w - 1, ry + h - 1, rx, ry + h - 1));
		double[][] bbb = MyLine.decomposer(imp2);

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

	//// ***************************************************************************************
	//// ***************************************************************************************

	static class CustomUtils {
		private static Roi roi = new Roi(0, 0, 0, 0);

		public static Plot drawPlot(TreeMap<Double, Double> map, String headline, String xName, String yName) {
			Set<Double> keys = map.keySet();

			double[] plotX = new double[keys.size()];
			double[] plotY = new double[keys.size()];
			int count = 0;
			for (Double key : keys) {

				plotX[count] = key;
				plotY[count] = map.get(key);
				count++;
			}
			double minX = Collections.min(keys);
			double maxX = Collections.max(keys);
			double minY = Collections.min(map.values());
			double maxY = Collections.max(map.values());
			double insetX = (maxX == 0) ? Math.abs(minX * 0.05) : Math.abs(maxX * 0.05);
			double insetY = (maxY == 0) ? Math.abs(minY * 0.05) : Math.abs(maxY * 0.05);

			Plot resultantGraph = new Plot(headline, xName, yName, plotX, plotY);
			// resultantGraph.setSize(550, 300);
			resultantGraph.setColor(Color.BLACK);
			// resultantGraph.setLimits( minX-insetX, maxX+insetX, minY-insetY,
			// maxY+insetY);
			resultantGraph.setLimitsToFit(true);
			Window topWindow = WindowManager.getFrontWindow();
			PlotWindow pw = resultantGraph.show();

			if (topWindow != null) {
				// MyLog.mark("X : " + pw.getX() + " Y : " + pw.getY());
				// IJ.log("X : " + pw.getX() + " Y : " + pw.getY());
				pw.setLocation(topWindow.getX() + 50, topWindow.getY() + 50);
				// MyLog.mark("X : " + pw.getX() + " Y : " + pw.getY());
				// IJ.log("X : " + pw.getX() + " Y : " + pw.getY());
			}
			pw.requestFocus();
			pw.toFront();

			return resultantGraph;
		}

		public static Plot drawContrastPlot(TreeMap<Double, ArrayList<Double>> map, int imageHeight, String headline,
				String xName, String yName) {

			Set<Double> keys = map.keySet();
			double[] plotX = new double[keys.size()];
			double[] plotY = new double[keys.size()];
			int count = 0;
			for (Double key : keys) {
				plotX[count] = key;
				plotY[count] = map.get(key).get(0); // 1 - to get sum
				count++;
			}

			Plot resultantGraph = new Plot(headline, xName, yName, plotX, plotY);
			resultantGraph.setSize(550, 300);
			resultantGraph.setColor(Color.BLACK);
			resultantGraph.setLimits(0.0, imageHeight / 2 + 200, 0.0, 1.3); /// Konstrast
			Window topWindow = WindowManager.getFrontWindow();
			PlotWindow pw = resultantGraph.show();

			if (topWindow != null) {
				MyLog.mark("X : " + pw.getX() + " Y : " + pw.getY());
				// IJ.log("X : " + pw.getX() + " Y : " + pw.getY());
				pw.setLocation(topWindow.getX() + 50, topWindow.getY() + 50);
				MyLog.mark("X : " + pw.getX() + " Y : " + pw.getY());
				// IJ.log("X : " + pw.getX() + " Y : " + pw.getY());
			}
			pw.requestFocus();
			pw.toFront();

			return resultantGraph;
		}

		static void addCurveToPlot(Plot plot, Color color, TreeMap<Double, Double> map) {
			Set<Double> keys = map.keySet();
			double[] plotX = new double[keys.size()];
			double[] plotY = new double[keys.size()];
			int count = 0;
			for (Double key : keys) {
				plotX[count] = key;
				plotY[count] = map.get(key);
				count++;
			}
			plot.setColor(color);
			plot.addPoints(plotX, plotY, Plot.LINE);

		}

		static void addCurveToContrastPlot(Plot plot, Color color, TreeMap<Double, ArrayList<Double>> map) {
			Set<Double> keys = map.keySet();
			double[] plotX = new double[keys.size()];
			double[] plotY = new double[keys.size()];
			int count = 0;
			for (Double key : keys) {
				plotX[count] = key;
				plotY[count] = map.get(key).get(0);
				count++;
			}
			plot.setColor(color);
			plot.addPoints(plotX, plotY, Plot.LINE);

		}

		public static double[] treemapToArray(TreeMap<Double, Double> map, boolean outX) {
			Set<Double> keys = map.keySet();
			double[] plotX = new double[keys.size()];
			double[] plotY = new double[keys.size()];
			int count = 0;
			for (Double key : keys) {
				plotX[count] = key;
				plotY[count] = map.get(key);
				count++;
			}
			if (outX) {
				return plotX;
			} else
				return plotY;
		}

		public static void resetRoiColor() {
			roi.setColor(Color.YELLOW);
		}

		public static void setDescriptionStyle(Component c) {
			Font f = new Font("SansSerif", Font.PLAIN, 11);
			c.setFont(f);
			c.setForeground(Color.DARK_GRAY);
		}

		public static boolean stringParsableToDouble(String s) {
			final String Digits = "(\\p{Digit}+)";
			final String HexDigits = "(\\p{XDigit}+)";
			// an exponent is 'e' or 'E' followed by an optionally
			// signed decimal integer.
			final String Exp = "[eE][+-]?" + Digits;
			final String fpRegex = ("[\\x00-\\x20]*" + // Optional leading
														// "whitespace"
					"[+-]?(" + // Optional sign character
					"NaN|" + // "NaN" string
					"Infinity|" + // "Infinity" string

					// A decimal floating-point string representing a finite
					// positive
					// number without a leading sign has at most five basic
					// pieces:
					// Digits . Digits ExponentPart FloatTypeSuffix
					//
					// Since this method allows integer-only strings as input
					// in addition to strings of floating-point literals, the
					// two sub-patterns below are simplifications of the grammar
					// productions from the Java Language Specification, 2nd
					// edition, section 3.10.2.

					// Digits ._opt Digits_opt ExponentPart_opt
					// FloatTypeSuffix_opt
					"(((" + Digits + "(\\.)?(" + Digits + "?)(" + Exp + ")?)|" +

					// . Digits ExponentPart_opt FloatTypeSuffix_opt
					"(\\.(" + Digits + ")(" + Exp + ")?)|" +

					// Hexadecimal strings
					"((" +
					// 0[xX] HexDigits ._opt BinaryExponent FloatTypeSuffix_opt
					"(0[xX]" + HexDigits + "(\\.)?)|" +

					// 0[xX] HexDigits_opt . HexDigits BinaryExponent
					// FloatTypeSuffix_opt
					"(0[xX]" + HexDigits + "?(\\.)" + HexDigits + ")" +

					")[pP][+-]?" + Digits + "))" + "[fFdD]?))" + "[\\x00-\\x20]*");// Optional
																					// trailing
																					// "whitespace"

			if (Pattern.matches(fpRegex, s)) {
				// Double.valueOf(myString); // Will not throw
				// NumberFormatException
				return true;
			}
			return false;
		}

		public boolean isInteger(String str) {
			if (str == null) {
				return false;
			}
			int length = str.length();
			if (length == 0) {
				return false;
			}
			int i = 0;
			if (str.charAt(0) == '-') {
				if (length == 1) {
					return false;
				}
				i = 1;
			}
			for (; i < length; i++) {
				char c = str.charAt(i);
				if (c <= '/' || c >= ':') {
					return false;
				}
			}
			return true;
		}

		public void checkColorMode(ImagePlus image) {

			int bitDepth = image.getBitDepth();
			MyLog.mark("" + bitDepth);
			if (bitDepth == 8 || bitDepth == 16) {
			} else {
				image.setProcessor(((ImageProcessor) (image.getProcessor().clone())).convertToByteProcessor());
			}
			bitDepth = image.getProcessor().getBitDepth();
			assert bitDepth == 8 || bitDepth == 16;
		}

		public static ImageProcessor cloneProcessor(ImagePlus img) {
			ImageProcessor proc = img.getProcessor();
			ImageProcessor originalProcessor = img.getBitDepth() == 16
					? new ShortProcessor(proc.getWidth(), proc.getHeight())
					: new ByteProcessor(proc.getWidth(), proc.getHeight());
			for (int i = 0; i < proc.getPixelCount(); i++) {
				originalProcessor.set(i, proc.get(i));
			}
			return originalProcessor;
		}

	}

	/////////////////////////////////////////////////////////////////

	private void gatherMTF(ImagePlus imp1, String angle, double quotient, ResultsTable rt1) {
		String postmortem = "";
		checkColorMode(imp1);
		postmortem = auxPath1 + "_postmortem.txt";
		MyLog.initLog(postmortem);
		MyLog.appendLog(postmortem, "**** gatherMTF *****");

		double preciseAngle = newEdgeAngle(imp1, "VERTICAL_ANGLE");

		Overlay overlay = new Overlay();
		overlay.clear();
		ImagePlus currentRegion = new ImagePlus("CurrentRegion");
		currentRegion = (ImagePlus) (imp1.clone());
		currentRegion.setRoi(0, 0, currentRegion.getWidth(), currentRegion.getHeight());
		ImageStatistics stat2 = currentRegion.getStatistics();

		// ==================================================================
		// iw2ayv USA THRESHOLD PER OTTENERE UNA IMMAGINE
		// BIANCO/NERO
		// DELLA ROI (16 oppure 8 bit/pixel)
		// ==================================================================

		ImageProcessor proc = currentRegion.getProcessor();
		MyLog.appendLog(postmortem,
				"currentRegion [" + currentRegion.getWidth() + "x" + currentRegion.getHeight() + "]");

		double[] bwValues;
		double[] pixelsValues;
		// ========================================================
		// olga: convert to Black&White
		// ========================================================
		if (proc.getBitDepth() == 16) {
			short[] sourcePixels = (short[]) proc.getPixels();
			pixelsValues = new double[sourcePixels.length];
			for (int i = 0; i < sourcePixels.length; i++) {
				pixelsValues[i] = sourcePixels[i] & 0xffff;
			}
			bwValues = new double[sourcePixels.length];
			double middleVal = proc.getAutoThreshold();
			MyLog.appendLog(postmortem, "threshold 16 bit Middle val: " + middleVal);

			for (int i = 0; i < sourcePixels.length; i++) {
				if (pixelsValues[i] <= middleVal) {
					bwValues[i] = 0.0;
				} else {
					bwValues[i] = 65635.0;
				}
			}
		} else {
			byte[] sourcePixels = (byte[]) proc.getPixels();
			pixelsValues = new double[sourcePixels.length];
			for (int i = 0; i < sourcePixels.length; i++) {
				pixelsValues[i] = sourcePixels[i] & 0xff;
			}
			bwValues = new double[sourcePixels.length];
			double middleVal = proc.getAutoThreshold();
			// middleVal = middleVal+middleVal*0.15;
			MyLog.appendLog(postmortem, "threshold 8 bit Middle val: " + middleVal);

			for (int i = 0; i < sourcePixels.length; i++) {
				if (pixelsValues[i] <= middleVal) {
					bwValues[i] = 0.0;
				} else {
					bwValues[i] = 255.0;
				}
			}
		}

		MyLog.appendLog(postmortem, "pixelsValues [" + pixelsValues.length + "]");

		// iw2ayv POTREBBERO ESSERE LE COORDINATE IN CUI IL
		// THRESHOLD
		// CAMBIA COLORE (CHE SO', CHIAMIAMOLO EDGE ?? (SE EDGE NON
		// PIACE LO CHIAMERO' PIETRO)

		ArrayList<Double> xVal = new ArrayList<Double>();
		ArrayList<Double> yVal = new ArrayList<Double>();
		// differentiation
		if (angle == "VERTICAL_ANGLE") {
			for (int i = 0; i < proc.getHeight(); i++) {
				double max = 0.0;
				double x = 0.0;
				for (int j = 1; j < proc.getWidth(); j++) {
					double diff = Math.abs(bwValues[j + i * proc.getWidth()] - bwValues[(j + i * proc.getWidth()) - 1]);
					if (diff > max) {
						max = diff;
						x = j;
					}
				}
				if (max != 0.) {
					xVal.add((double) x);
					yVal.add((double) i);
				}
			}
		} else {
			for (int i = 0; i < proc.getWidth(); i++) {
				double max = 0.0;
				double y = 0.0;
				for (int j = 1; j < proc.getHeight(); j++) {
					double diff = Math
							.abs(bwValues[i + j * proc.getWidth()] - bwValues[(i + (j - 1) * proc.getWidth())]);
					if (diff > max) {
						max = diff;
						y = j;
					}
				}
				if (max != 0.) {
					xVal.add((double) i);
					yVal.add((double) y);
				}
			}
		}
		double[] xArr = new double[xVal.size() - 1];
		double[] yArr = new double[yVal.size() - 1];
		String aux1 = "";
		MyLog.appendLog(postmortem, "----EDGE detection [" + xVal.size() + "]------");

		///// NOTA BENE MESSO UN PIXEL IN MENO X CHE SBALLAVA FIT ANGOLI PICCOLI
		///// (valore errato nell'ultimo pixel)
		for (int i = 0; i < xVal.size() - 1; i++) {
			xArr[i] = xVal.get(i);
			yArr[i] = yVal.get(i);
			aux1 = "xArr[" + i + "]= " + xArr[i] + " yArr[" + i + "]= " + yArr[i];
			MyLog.appendLog(postmortem, aux1);
			// IJ.log(aux1);
		}
		MyLog.appendLog(postmortem, "------");

		// iw2ayv FIT DELL 'IIMAGINE PER TROVARE IL LIMITE
		// CHIARO/SCURO
		// E CALCOLARNE L'ANGOLO (PECCHE' ???)
		// find angle and line
		CurveFitter lineFitter = new CurveFitter(xArr, yArr);
		lineFitter.doFit(CurveFitter.STRAIGHT_LINE);

		// draw Angle

		Roi rect = new Roi(0, 0, currentRegion.getWidth(), currentRegion.getHeight());
		Roi line;
		if (angle == "VERTICAL_ANGLE")
			line = new Line((1 - lineFitter.getParams()[0]) / lineFitter.getParams()[1], 1,
					(proc.getHeight() - 1 - lineFitter.getParams()[0]) / lineFitter.getParams()[1],
					proc.getHeight() - 1);
		else
			line = new Line(1, lineFitter.getParams()[0] + lineFitter.getParams()[1], proc.getWidth() - 1,
					lineFitter.getParams()[0] + lineFitter.getParams()[1] * (proc.getWidth() - 1));
		if (angle == "VERTICAL_ANGLE") {
			rect.setStrokeColor(Color.RED);
			line.setStrokeColor(Color.RED);
		} else {
			rect.setStrokeColor(Color.GREEN);
			line.setStrokeColor(Color.GREEN);
		}
		overlay.add(rect);
		overlay.add(line);

		proc = (ImageProcessor) (currentRegion.getProcessor().clone());
		double angleGrad = Math.toDegrees(Math.atan(1.0 / (lineFitter.getParams())[1]));

		// double angleGrad1 = -2.338;
		// IJ.log(lineFitter.getResultString());
		// IJ.log(angel);

		double[] distanceToLine = new double[pixelsValues.length]; // opredelyem
																	// rastoyanie
																	// do
																	// linii
		// iw2ayv LA PRECEDENTE SCRITTA SIGNIFICA: DETERMINARE LA
		// DISTANZA DALLA LINEA (GOOGLE DIXIT)

		double pixelSize = ReadDicom.readDouble(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));

		// pixelSizeNormalized = pixelSize * k;

		double roiAngleRadiants = angleGrad * Math.PI / 180;
		MyLog.appendLog(postmortem, "Linear fit over Edge, Angle: " + angleGrad);
		String aux3 = "";
		MyLog.appendLog(postmortem, "------ Distance to line [" + distanceToLine.length + "]-----");
		for (int i1 = 0; i1 < proc.getHeight(); i1++) {
			for (int i2 = 0; i2 < proc.getWidth(); i2++) {
				// distance to line for every pixel;
				distanceToLine[i2 + i1 * proc.getWidth()] = pixelSize * 1
						* (i2 * Math.cos(roiAngleRadiants) - i1 * Math.sin(roiAngleRadiants));
				aux3 = aux3 + distanceToLine[i2 + i1 * proc.getWidth()] + " ";

				// if(j+i*proc.getWidth() < 400)IJ.log("x="
				// + j+" y="+i+" "
				// +distanceToLine[j+i*proc.getWidth()]);
			}
		}

		// ================================================================
		// iw2ayv la DistanceValueMap contiene il valore dei pixel,
		// con come chiave la distanza dalla linea NOTA BENE SONO
		// BINARIZZATI: BIANCO o NERO
		// ================================================================

		TreeMap<Double, Double> distanceValueMap = new TreeMap<Double, Double>();
		for (int i = 0; i < pixelsValues.length; i++) {
			distanceValueMap.put(distanceToLine[i], (double) pixelsValues[i]);
			// if(i<200)IJ.log("KEY:"+proection[i]+"
			// "+"VALUE:"+distanceValueBeinding.get(proection[i]));
		}

		MyLog.appendLog(postmortem, "makeProjection(ImagePlus currentRegion)");

		double deltaS = quotient * pixelSize; // ����� � ������
												// �������
		int k = 0;
		if (distanceValueMap.firstKey() < 0) {
			if (distanceValueMap.firstKey() < -deltaS / 2) {
				k = -((int) ((Math.abs(distanceValueMap.firstKey()) - deltaS / 2) / deltaS) + 1);
				MyLog.appendLog(postmortem, "distanceValueMap.firstKey() < 0: " + k);
			}
		}
		Set<Double> DVkeys = distanceValueMap.keySet();
		// IJ.log(distanceValueMap);
		MyLog.appendLog(postmortem, "----- distanceValueMap [" + distanceValueMap.size() + "]------");

		/***
		 * AVERAGE MAP _________ ESF
		 */

		TreeMap<Double, Double> averageMap = new TreeMap<Double, Double>();
		double avg = 0;
		int count = 0;
		for (Double distance : DVkeys) {
			if (Math.abs(distance - k * deltaS) <= deltaS / 2) {
				avg = avg + distanceValueMap.get(distance);
				count++;
			} else {
				if (count > 0) {
					avg = avg / count;
					averageMap.put((double) k, avg);
					k++;
					avg = 0;
					count = 0;
				} else {
					count = 0;
					k++;
				}
			}
		}

		MyLog.appendLog(postmortem, "----- averageMap [" + averageMap.size() + "]------");
		for (Entry<Double, Double> entry : averageMap.entrySet()) {
			MyLog.appendLog(postmortem, "averageMap Key: " + entry.getKey() + "  Value: " + entry.getValue());
		}
		MyLog.appendLog(postmortem, "-------");

		// calculate LSF
		Set<Double> AMkeys = averageMap.keySet();
		TreeMap<Double, Double> difrMap = new TreeMap<Double, Double>();
		double previousVal = 0.0;
		Iterator<Double> AMKeys = AMkeys.iterator();
		if (AMKeys.hasNext()) {
			previousVal = averageMap.get(AMKeys.next());
		}
		while (AMKeys.hasNext()) {
			double AMkey = (double) AMKeys.next();
			if (AMKeys.hasNext()) {
				difrMap.put(AMkey, (averageMap.get(averageMap.higherKey(AMkey)) - previousVal) / 2);// *deltaS);
				previousVal = averageMap.get(AMkey);
			} else {
				difrMap.put(AMkey, 0.0);
			}
		}

		MyLog.appendLog(postmortem, "----- firstDerivative difrMap [" + difrMap.size() + "] ------");
		for (Entry<Double, Double> entry : difrMap.entrySet()) {
			MyLog.appendLog(postmortem, "difrMap Key: " + entry.getKey() + "  Value: " + entry.getValue());
		}
		MyLog.appendLog(postmortem, "-------");
		int j1 = 0;
		double[] vetDifrKey = new double[difrMap.size() * 2];
		double[] vetDifrValue = new double[difrMap.size() * 2];
		for (Entry<Double, Double> entry : difrMap.entrySet()) {
			vetDifrKey[j1] = entry.getKey();
			vetDifrValue[j1] = entry.getValue();
			j1++;
		}

		TreeMap<Double, Double> difrMap2 = new TreeMap<Double, Double>();
		int i2 = 0;
		double aux2 = 0;
		for (int i1 = 0; i1 < (vetDifrKey.length - 3); i1++) {
			if (i1 % 2 == 0) {
				difrMap2.put((double) i1, vetDifrValue[i2]);
				i2++;
			} else {
				aux2 = ((0.375 * vetDifrValue[(i2 - 1)]) + (0.75 * vetDifrValue[(i2)])
						- (0.125 * vetDifrValue[(i2 + 1)]));
				difrMap2.put((double) i1, aux2);
			}
		}

		MyLog.appendLog(postmortem, "----- firstDerivative difrMap2 [" + difrMap2.size() + "] ------");
		for (Entry<Double, Double> entry : difrMap2.entrySet()) {
			MyLog.appendLog(postmortem, "difrMap2 Key: " + entry.getKey() + "  Value: " + entry.getValue());
		}
		MyLog.appendLog(postmortem, "-------");
		MyLog.appendLog(postmortem, "calcLineSpread()");

		/***
		 * iw2ayv: DFT = Discrete Fourier Transform ????
		 */

		double n = difrMap2.size();
		double h = deltaS;
		double T = n * h;
		double DFT[] = new double[(int) n / 2];
		for (int j = 0; j < (int) n / 2; j++) {
			Set<Double> DMkeys = difrMap2.keySet();
			double fs = 0.0;
			double ss = 0.0;
			k = 0;
			for (Double DMkey : DMkeys) {
				fs = fs + difrMap2.get(DMkey) * Math.cos(2 * Math.PI * j * k / n);
				ss = ss + difrMap2.get(DMkey) * Math.sin(2 * Math.PI * j * k / n);
				k++;
			}
			DFT[j] = Math.abs(Math.sqrt(Math.pow(fs, 2.0) + Math.pow(ss, 2.0)));
		}

		MyLog.appendLog(postmortem, "------- DFT[" + DFT.length + "]----------");
		for (int i1 = 0; i1 < DFT.length; i1++) {
			MyLog.appendLog(postmortem, "i1: " + i1 + " DFT: " + DFT[i1]);
		}
		MyLog.appendLog(postmortem, "-------");

		/****
		 * iw2ayv: qui avviene la NORMALIZZAZIONE ?????
		 */

		double normalizer = 1 / DFT[0];
		// double [] normalizedDFT = new double [DFT.length];
		TreeMap<Double, Double> intensityMTFMap = new TreeMap<Double, Double>();

		// IJ.log("quotient= " + quotient);
		for (int i = 0; i < DFT.length * quotient; i++) {
			double IMkey = (double) i / (DFT.length * quotient);
			double val = DFT[i] * normalizer;
			intensityMTFMap.put(IMkey, val);
		}

		// IJ.log("calcMTF()");
		MyLog.appendLog(postmortem, "----- intensityMTFMap [" + intensityMTFMap.size() + "]------");
		for (Entry<Double, Double> entry : intensityMTFMap.entrySet()) {
			MyLog.appendLog(postmortem, "intensityMTFMap Key: " + entry.getKey() + "  Value: " + entry.getValue());
			// IJ.log("intensityMTFMap Key: " + entry.getKey() + " Value: " +
			// entry.getValue());
		}
		MyLog.appendLog(postmortem, "-------");
		// IJ.log("------");

		double[] MTF_X = null;
		double[] MTF_Y = null;
		double[] AVE_X = null;
		double[] AVE_Y = null;
		double[] DIF_X = null;
		double[] DIF_Y = null;
		// for (Entry<Double, Double> entry : intensityMTFMap.entrySet()) {
		// MyLog.appendLog(postmortem, "intensityMTFMap Key: " + entry.getKey()
		// + "
		// Value: " + entry.getValue());

		if (rt1 == null) {
		} else {

			String t1 = "TESTO";
			String s2 = "VALORE";
			String s3 = "MTF_X";
			String s4 = "MTF_Y";
			String s5 = "AVE_X";
			String s6 = "AVE_Y";
			String s7 = "DIF_X";
			String s8 = "DIF_Y";
			String s9 = "roi_x";
			String s10 = "roi_y";
			String s11 = "roi_b";
			String s12 = "roi_h";

			rt1.addValue(t1, "EDGE_ANGLE");
			rt1.addValue(s2, angleGrad);
			rt1.incrementCounter();
			rt1.addValue(t1, "SAMPLE_SIZE");
			rt1.addValue(s2, "-");

			// if (intensityMTFMap == null) {
			// MyLog.waitHere("IMPOSSIBILE TROVARE LA MTF!!!");
			// return;
			// }

			MTF_X = CustomUtils.treemapToArray(intensityMTFMap, true);
			MTF_Y = CustomUtils.treemapToArray(intensityMTFMap, false);
			AVE_X = CustomUtils.treemapToArray(averageMap, true);
			AVE_Y = CustomUtils.treemapToArray(averageMap, false);
			DIF_X = CustomUtils.treemapToArray(difrMap, true);
			DIF_Y = CustomUtils.treemapToArray(difrMap, false);

			for (int i1 = 0; i1 < MTF_X.length; i1++) {
				rt1.incrementCounter();
				rt1.addValue(t1, "PLOT_" + i1);
				rt1.addValue(s3, MTF_X[i1]);
				rt1.addValue(s4, MTF_Y[i1]);
				rt1.addValue(s5, AVE_X[i1]);
				rt1.addValue(s6, AVE_Y[i1]);
				rt1.addValue(s7, DIF_X[i1]);
				rt1.addValue(s8, DIF_Y[i1]);
				rt1.addValue(s9, stat2.roiX);
				rt1.addValue(s10, stat2.roiY);
				rt1.addValue(s11, stat2.roiWidth);
				rt1.addValue(s12, stat2.roiHeight);

			}
			// }
		}

		MyLog.waitHere("ESF= " + averageMap.size() + " LSF= " + difrMap.size() + " MTF= " + intensityMTFMap.size());

		CustomUtils.drawPlot(averageMap, "Edge Spread Function", "pixels", "intensity");
		CustomUtils.drawPlot(difrMap, "Line Spread Function", "pixels", "values");
		CustomUtils.drawPlot(intensityMTFMap, "Modulation Transfer Function (normalized)", "frequency", "intensity");
		CustomUtils.resetRoiColor();
		MyLog.waitHere("FINITO ??");

	}

	private void checkColorMode(ImagePlus img) {
		// ImagePlus img = WindowManager.getImage(imageID);
		int bitDepth = img.getBitDepth();
		// if(bitDepth == ImagePlus.COLOR_256 || bitDepth ==
		// ImagePlus.COLOR_RGB
		// || bitDepth == ImagePlus.GRAY32){
		// IJ.log("bitDepth= " + bitDepth);
		// IJ.log(ImagePlus.GRAY16);
		if (bitDepth == 8 || bitDepth == 16) {
			img.setProcessor(((ImageProcessor) (img.getProcessor().clone())));
		} else {
			img.setProcessor(((ImageProcessor) (img.getProcessor().clone())).convertToByteProcessor());
		}
		bitDepth = img.getProcessor().getBitDepth();
		assert bitDepth == 8 || bitDepth == 16;
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

}
