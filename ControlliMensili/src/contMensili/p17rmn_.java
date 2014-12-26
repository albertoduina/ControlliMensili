package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.ImageCalculator;
import ij.plugin.PlugIn;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.CustomCanvasGeneric;
import utils.HarrisCornerDetector;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyAutoThreshold;
import utils.MyCircleDetector;
import utils.MyConst;
import utils.MyGeometry;
import utils.MyLog;
import utils.MyMsg;
import utils.MyRats;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableSequence;
import utils.UtilAyv;

/*
 * Copyright (C) 2007 Alberto Duina, SPEDALI CIVILI DI BRESCIA, Brescia ITALY
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/**
 * Analizza il WARP , le rods vengono selezionate in automatico
 * 
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */

public class p17rmn_ implements PlugIn, Measurements {

	static final int ABORT = 1;

	public static String VERSION = "WARP";

	private static String TYPE = " >> CONTROLLO WARP____________________";

	// ---------------------------"01234567890123456789012345678901234567890"
	private static boolean debug = true;

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.xls
	 */
	private static String fileDir = "";

	/**
	 * tabella coi dati di ayv.txt (generati da Sequenze)
	 */
	// String[][] iw2ayvTable;

	private static Color[] c1 = { Color.red, Color.orange, Color.yellow,
			Color.green, Color.cyan, Color.blue, Color.magenta, Color.pink };

	/**
	 * immagine da analizzare
	 */
	ImagePlus imp1;

	public void run(String args) {

		UtilAyv.setMyPrecision();

		if (IJ.versionLessThan("1.43k"))
			return;
		//
		// nota bene: le seguenti istruzioni devono essere all'inizio, in questo
		// modo il messaggio viene emesso, altrimenti si ha una eccezione
		//
		try {
			Class.forName("utils.IW2AYV");
		} catch (ClassNotFoundException e) {
			IJ.error("ATTENZIONE, manca il file iw2ayv_xxx.jar");
			return;
		}

		String className = this.getClass().getName();

		VERSION = className + "_build_" + MyVersion.getVersion()
				+ "_iw2ayv_build_" + MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}
	}

	public int manualMenu(int preset, String testDirectory) {
		boolean retry = false;
		boolean step = false;
		int riga1 = 0;
		do {

			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox().about("Controllo Warp", this.getClass());
				new AboutBox().about("Controllo Warp",
						MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				selfTestAyv();
				retry = true;
				break;
			case 4:
				step = true;
			case 5:
				String path1 = UtilAyv
						.imageSelection("SELEZIONARE IMMAGINE...");
				if (path1 == null)
					return 5;
				boolean autoCalled = false;
				boolean verbose = true;
				boolean test = false;
				boolean demo = true;
				boolean silent = false;
				int timeout = 0;
				mainWarp(path1, autoCalled, step, silent, verbose, test, demo,
						timeout);
				UtilAyv.afterWork();
				retry = false;
				return 5;
			}
		} while (retry);
		UtilAyv.afterWork();
		new AboutBox().close();
		return 0;
	}

	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p17 riceve " + autoArgs);

		boolean fast = Prefs.get("prefer.fast", "false").equals("true") ? true
				: false;

		ResultsTable result1 = null;
		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);

		StringTokenizer st = new StringTokenizer(autoArgs, "#");
		int nTokens = st.countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p7rmn_");
			selfTestSilent();
			return 0;
		}
		if (nTokens > 1) {
			MyMsg.msgParamError();
			return 0;
		}
		boolean retry = false;
		boolean step = false;
		if (fast) {
			retry = false;
			boolean autoCalled = true;
			boolean demo = false;
			boolean test = false;
			boolean silent = false;
			boolean verbose = true;
			int timeout = 3000;

			String path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);

			result1 = mainWarp(path1, autoCalled, step, silent, verbose, test,
					demo, timeout);

			if (!(result1 == null))
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, result1);

			UtilAyv.afterWork();

		} else {
			do {
				// int userSelection1 = UtilAyv.userSelectionAuto(VERSION,
				// TYPE);
				int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]),
						TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

				switch (userSelection1) {
				case ABORT:
					new AboutBox().close();
					return 0;
				case 2:
					// new AboutBox().about("Controllo Warp", this.getClass());
					new AboutBox().about("Controllo Warp",
							MyVersion.CURRENT_VERSION);
					retry = true;
					break;
				case 3:
					step = true;
				case 4:
					boolean verbose = true;
					boolean test = false;
					boolean autoCalled = true;
					iw2ayvTable = new TableSequence().loadTable(fileDir
							+ MyConst.SEQUENZE_FILE);
					String path1 = TableSequence.getPath(iw2ayvTable,
							vetRiga[0]);
					// ResultsTable rt = mainWarp(path1], autoCalled, step,
					// verbose,
					// test);
					// UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);

					UtilAyv.afterWork();
					retry = false;
					break;
				}
			} while (retry);
		}
		UtilAyv.afterWork();
		new AboutBox().close();
		return 0;
	}

	@SuppressWarnings("deprecation")
	public static ResultsTable mainWarp(String path1, boolean autoCalled,
			boolean step, boolean silent, boolean verbose, boolean test,
			boolean demo, int timeout) {
		boolean accetta = false;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		ResultsTable rt = null;
		double dimPixel2 = 0;
		// MyLog.waitHere("mainWarp " + path1);

		// --------------------------------------------------------------------------------------/
		// Qui si torna se la misura è da rifare
		// --------------------------------------------------------------------------------------/

		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, verbose);

		int diam = 10;
		ResultsTable rt1 = p17rmn_.automaticRoiPreparation4(imp1, diam, silent,
				timeout, demo);
		if (rt1 == null) {
			imp1.show();
			MyLog.waitHere("tabPunti==null");
			return null;
		}
		dimPixel2 = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));
		String slicePos = ReadDicom.readSubstring(ReadDicom.readDicomParameter(
				imp1, MyConst.DICOM_IMAGE_POSITION), 3);
		int nPunti = rt1.getCounter();

		String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);

		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp1,
				tabCodici, VERSION, autoCalled);
		rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);
		String t1 = "TESTO";
		String s2 = "coord_x";
		String s3 = "coord_y";
		String s4 = "dummy1";
		String s5 = "dummy2";
		String s6 = "dummy3";
		String s7 = "dummy4";

		rt.addLabel(t1, "ShiftCentrat");
		rt.addValue(s2, UtilAyv.convertToDouble(slicePos));
		String aux1 = "";
		int aux2 = 0;
		for (int i2 = 0; i2 < MyConst.P7_NUM_RODS; i2 = i2 + 2) {
			aux2++;
			aux1 = "Rod" + aux2 + "a";
			rt.incrementCounter();
			rt.addLabel(t1, aux1);
			// rt.addValue(s2, tabPunti[i2][0]);
			// rt.addValue(s3, tabPunti[i2][1]);
			rt.addValue(s4, 0);
			rt.addValue(s5, 0);
			rt.addValue(s6, 0);
			rt.addValue(s7, 0);
			rt.incrementCounter();
			aux1 = "Rod" + aux2 + "b";
			rt.addLabel(t1, aux1);
			// rt.addValue(s2, tabPunti[i2 + 1][0]);
			// rt.addValue(s3, tabPunti[i2 + 1][1]);
			rt.addValue(s4, 0);
			rt.addValue(s5, 0);
			rt.addValue(s6, 0);
			rt.addValue(s7, 0);
		}
		aux2 = 0;
		for (int i2 = MyConst.P7_NUM_RODS; i2 < MyConst.P7_TOTAL_NUM_POINTS; i2++) {
			aux2++;
			aux1 = "Cubo" + aux2;
			rt.incrementCounter();
			rt.addLabel(t1, aux1);
			// rt.addValue(s2, tabPunti[i2][0]);
			// rt.addValue(s3, tabPunti[i2][1]);
		}
		rt.incrementCounter();
		rt.addLabel(t1, "Spacing");
		rt.addValue(s2, dimPixel2);
		if (verbose && !test)
			rt.show("Results");

		return rt;
	}

	/***
	 * Ricerca automatica delle posizioni rods con AutoTreshold ed
	 * AnalyzeParticles, può utilizzare diverse strategie (valori di preset) se
	 * la prima dovesse fallire
	 * 
	 * @param imp1
	 * @param diam2
	 * @param timeout
	 * @param demo
	 */
	public static ResultsTable automaticRoiPreparation4(ImagePlus imp1,
			int diam2, boolean silent, int timeout, boolean demo) {

		ImagePlus imp4 = imp1.duplicate();
		Overlay over1 = new Overlay();
		imp1.setOverlay(over1);
		boolean verbose = false;

		int numRods1 = 32;

		// if (demo) {
		// UtilAyv.showImageMaximized(imp4);
		// MyLog.waitHere(listaMessaggi(0), debug, timeout);
		// }

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));

		// -------------------------------------------------------------------
		// REMEMBER: PER USARE ANALYZE PARTICLES DOBBIAMO
		// ANALIZZARE OGGETTI NERI SU SFONDO BIANCO
		// dovrei ottenere per l'esterno 32 oggetti con area
		// nel range tra 5.0 e 12.0 e per l'interno 4 oggetti
		// altrimenti effettuerò il threshold manuale
		// UTILIZZO 5 diverse strategie (in realtà sono un mix tra i settaggi di
		// MyAutoThreshold e l'uso di invert
		// -------------------------------------------------------------------

		ImagePlus[] imp9 = null;
		ImagePlus imp10 = null;
		ImagePlus imp11 = null;
		ImagePlus imp12 = null;
		ImagePlus imp13 = null;
		ResultsTable rt9 = null;
		ResultsTable rt19 = null;
		ResultsTable rt10 = null;
		ResultsTable rt11 = null;
		ResultsTable rt12 = null;
		ResultsTable rt13 = null;
		int trovati9 = -1;
		int trovati19 = -1;
		int trovati10 = -1;
		int trovati11 = -1;
		int trovati12 = -1;
		int trovati13 = -1;
		ImagePlus impOut = null;
		ResultsTable rtOut = null;

		boolean cerca = true;
		// creo anche un vettore di ImagePlus (non uno stack)

		// per prima cosa effettuo il threshold "GENERALE"

		if (!imp1.isVisible())
			UtilAyv.showImageMaximized(imp1);

		if (cerca) {
			imp9 = strategiaGENERALE(imp4);
			rt9 = analisi(imp9[0], verbose, timeout, 32);
			rt19 = analisi(imp9[0], verbose, timeout, 4);
			if (rt9 != null) {
				trovati9 = rt9.getCounter();
				trovati19 = rt19.getCounter();
				// UtilAyv.showImageMaximized(imp9);
				rt9.show("Results");
				// MyLog.waitHere();
			}
			// MyLog.waitHere("trovati9= " + trovati9);
			if (trovati9 == numRods1) {
				cerca = false;
				// impOut = imp9;
				rtOut = rt9;
				MyLog.waitHere("sufficiente strategia generale");
			}
			if(cerca) MyLog.waitHere("fallita strategia generale");
		}

		// MyLog.waitHere();

		if (cerca) {
			imp10 = strategiaSIEMENS(imp4);
			rt10 = analisi(imp10, verbose, timeout, numRods1);
			if (rt10 != null) {
				trovati10 = rt10.getCounter();
				// UtilAyv.showImageMaximized(imp10);
				// rt10.show("Results");
			}
			// MyLog.waitHere("trovati10= " + trovati10);
			if (trovati10 == numRods1) {
				cerca = false;
				impOut = imp10;
				rtOut = rt10;
				// MyLog.waitHere("sufficiente strategia SIEMENS");
			}
			if(cerca) MyLog.waitHere("fallita strategia SIEMENS");
		}

		if (cerca) {
			imp11 = strategiaHITACHI(imp4);
			rt11 = analisi(imp11, verbose, timeout, numRods1);
			if (rt11 != null)
				trovati11 = rt11.getCounter();
			if (trovati11 == numRods1) {
				cerca = false;
				impOut = imp11;
				rtOut = rt11;
				// MyLog.waitHere("sufficiente strategia HITACHI");
			}
			if(cerca) MyLog.waitHere("fallita strategia HITACHI");
		}

		if (cerca) {
			imp12 = strategiaGEMS(imp4);
			rt12 = analisi(imp12, verbose, timeout, numRods1);
			if (rt12 != null)
				trovati12 = rt12.getCounter();
			if (trovati12 == numRods1) {
				cerca = false;
				impOut = imp12;
				rtOut = rt12;
				// MyLog.waitHere("sufficiente strategia GEMS");
			}
			if(cerca) MyLog.waitHere("fallita strategia GEMS");
		}

		if (cerca) {
			imp11 = strategiaHITACHI2(imp4);
			rt13 = analisi(imp11, verbose, timeout, numRods1);
			if (rt13 != null)
				trovati13 = rt13.getCounter();
			if (trovati13 == numRods1) {
				cerca = false;
				impOut = imp13;
				rtOut = rt13;
				// MyLog.waitHere("sufficiente strategia HITACHI2");
			}
			if(cerca) MyLog.waitHere("fallita strategia HITACHI2");
		}

		if (cerca)
			MyLog.waitHere("trovato un Kaiser");

		// / ora marco le posizioni delle 32 ROI

		Calibration cal = imp1.getCalibration();

		int xcol = rtOut.getColumnIndex("XM");
		int ycol = rtOut.getColumnIndex("YM");
		//
		double[] vetX = rtOut.getColumnAsDoubles(xcol);
		double[] vetY = rtOut.getColumnAsDoubles(ycol);

		double[] vetX2 = new double[vetX.length];
		double[] vetY2 = new double[vetY.length];
		for (int i1 = 0; i1 < vetX.length; i1++) {
			vetX2[i1] = cal.getRawX(vetX[i1]);
			vetY2[i1] = cal.getRawY(vetY[i1]);
		}

		float[] vetXf = UtilAyv.toFloat(vetX2);
		float[] vetYf = UtilAyv.toFloat(vetY2);

		imp1.setRoi(new PointRoi(vetXf, vetYf, vetXf.length));
		imp1.getRoi().setStrokeColor(Color.yellow);
		over1.addElement(imp1.getRoi());
		imp1.deleteRoi();

		MyLog.waitHere("rod esterne trovate= " + rtOut.getCounter());

		return rtOut;
	}

	/***
	 * Ricerca automatica delle posizioni gels con AutoTreshold ed
	 * AnalyzeParticles
	 * 
	 * @param imp1
	 * @param diam2
	 * @param timeout
	 * @param demo
	 */
	public static ResultsTable automaticRoiPreparation3(ImagePlus imp1,
			int diam2, boolean silent, int timeout, boolean demo) {

		ImagePlus imp4 = imp1.duplicate();
		Overlay over1 = new Overlay();
		imp1.setOverlay(over1);
		// Overlay over2 = new Overlay();
		// imp5.setOverlay(over2);
		boolean verbose = false;

		int numRods1 = 36;

		// if (demo) {
		// UtilAyv.showImageMaximized(imp4);
		// MyLog.waitHere(listaMessaggi(0), debug, timeout);
		// }

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));

		// -------------------------------------------------------------------
		// REMEMBER: PER USARE ANALYZE PARTICLES DOBBIAMO
		// ANALIZZARE OGGETTI NERI SU SFONDO BIANCO
		// dovrei ottenere per l'esterno 32 oggetti con area
		// nel range tra 5.0 e 12.0 e per l'interno 4 oggetti
		// altrimenti effettuerò il threshold manuale
		// UTILIZZO 5 diverse strategie (in realtà sono un mix tra i settaggi di
		// MyAutoThreshold e l'uso di invert
		// -------------------------------------------------------------------

		ImagePlus[] imp9 = null;
		ImagePlus imp10 = null;
		ImagePlus imp11 = null;
		ImagePlus imp12 = null;
		ImagePlus imp13 = null;
		ResultsTable rt9 = null;
		ResultsTable rt19 = null;
		ResultsTable rt10 = null;
		ResultsTable rt11 = null;
		ResultsTable rt12 = null;
		ResultsTable rt13 = null;
		int trovati9 = -1;
		int trovati19 = -1;
		int trovati10 = -1;
		int trovati11 = -1;
		int trovati12 = -1;
		int trovati13 = -1;
		ImagePlus impOut = null;
		ResultsTable rtOut = null;

		boolean cerca = true;
		// creo anche un vettore di ImagePlus (non uno stack)

		// per prima cosa effettuo il threshold "GENERALE"

		if (!imp1.isVisible())
			UtilAyv.showImageMaximized(imp1);

		if (cerca) {
			imp9 = strategiaGENERALE(imp4);
			rt9 = analisi(imp9[0], verbose, timeout, 32);
			rt19 = analisi(imp9[0], verbose, timeout, 4);
			if (rt9 != null) {
				trovati9 = rt9.getCounter();
				trovati19 = rt19.getCounter();
				// UtilAyv.showImageMaximized(imp9);
				rt9.show("Results");
				// MyLog.waitHere();
			}
			// MyLog.waitHere("trovati9= " + trovati9);
			if ((trovati9 == 32) && (trovati19 == 4)) {
				cerca = false;
				// impOut = imp9;
				rtOut = rt9;
				// MyLog.waitHere("sufficiente strategia generale");
			}
		}

		// MyLog.waitHere();

		if (cerca) {
			imp10 = strategiaSIEMENS(imp4);
			rt10 = analisi(imp10, verbose, timeout, numRods1);
			if (rt10 != null) {
				trovati10 = rt10.getCounter();
				// UtilAyv.showImageMaximized(imp10);
				// rt10.show("Results");
			}
			// MyLog.waitHere("trovati10= " + trovati10);
			if (trovati10 == numRods1) {
				cerca = false;
				impOut = imp10;
				rtOut = rt10;
				// MyLog.waitHere("sufficiente strategia SIEMENS");
			}
		}

		if (cerca) {
			imp11 = strategiaHITACHI(imp4);
			rt11 = analisi(imp11, verbose, timeout, numRods1);
			if (rt11 != null)
				trovati11 = rt11.getCounter();
			if (trovati11 == numRods1) {
				cerca = false;
				impOut = imp11;
				rtOut = rt11;
				// MyLog.waitHere("sufficiente strategia HITACHI");
			}
		}

		if (cerca) {
			imp12 = strategiaGEMS(imp4);
			rt12 = analisi(imp12, verbose, timeout, numRods1);
			if (rt12 != null)
				trovati12 = rt12.getCounter();
			if (trovati12 == numRods1) {
				cerca = false;
				impOut = imp12;
				rtOut = rt12;
				// MyLog.waitHere("sufficiente strategia HITACHI");
			}
		}

		if (cerca) {
			imp11 = strategiaHITACHI2(imp4);
			rt13 = analisi(imp11, verbose, timeout, numRods1);
			if (rt13 != null)
				trovati13 = rt13.getCounter();
			if (trovati13 == numRods1) {
				cerca = false;
				impOut = imp13;
				rtOut = rt13;
				// MyLog.waitHere("sufficiente strategia HITACHI");
			}
		}

		MyLog.waitHere("questi sono i 32 esterni");

		// se nessuna delle strategie ha trovato tutte le roi, accetto la
		// strategia migliore, poi interverrà l'operatore a correggere e/o
		// completare le ROI

		int[] array1 = new int[5];
		array1[0] = Math.abs(trovati9 - numRods1);
		array1[1] = Math.abs(trovati10 - numRods1);
		array1[2] = Math.abs(trovati11 - numRods1);
		array1[3] = Math.abs(trovati12 - numRods1);
		array1[4] = Math.abs(trovati13 - numRods1);
		int posMin = posMinValue(array1);
		// if (posMin > 0)
		// MyLog.waitHere("posMin=" + posMin);

		switch (posMin) {
		case 0:
			impOut = imp9[0];
			rtOut = rt9;
			break;
		case 1:
			impOut = imp10;
			rtOut = rt10;
			UtilAyv.showImageMaximized(impOut);
			// MyLog.waitHere();
			break;
		case 2:
			impOut = imp11;
			rtOut = rt11;
			UtilAyv.showImageMaximized(impOut);
			// MyLog.waitHere();
			break;
		case 3:
			impOut = imp12;
			rtOut = rt12;
			UtilAyv.showImageMaximized(impOut);
			// MyLog.waitHere();
			break;
		case 4:
			impOut = imp13;
			rtOut = rt13;
			UtilAyv.showImageMaximized(impOut);
			// MyLog.waitHere();
			break;
		}

		if (rtOut == null)
			MyLog.waitHere(">>>> INFERNAL ERROR <<<<<");

		// ====================== centrale ==================
		//
		rtOut.show("Results");
		MyLog.waitHere("verificare ResultsTable");

		//
		int xcol = rtOut.getColumnIndex("XM");
		int ycol = rtOut.getColumnIndex("YM");
		//
		double[] vetX = rtOut.getColumnAsDoubles(xcol);
		// MyLog.logVector(vetX, "vetX");
		//
		double[] vetY = rtOut.getColumnAsDoubles(ycol);
		// MyLog.logVector(vetY, "vetY");
		// MyLog.waitHere();
		//

		double[] vetx1 = new double[vetX.length];
		double[] vety1 = new double[vetX.length];
		for (int i1 = 0; i1 < vetX.length; i1++) {
			vetx1[i1] = vetX[i1] / dimPixel;
			vety1[i1] = vetY[i1] / dimPixel;
		}

		Roi[] vetRoi = new Roi[vetX.length];
		for (int i1 = 0; i1 < vetX.length; i1++) {
			imp1.setRoi(new OvalRoi(vetx1[i1] - diam2 / 2, vety1[i1] - diam2
					/ 2, diam2, diam2));
			vetRoi[i1] = imp1.getRoi();
			imp1.getRoi().setStrokeColor(Color.green);
			over1.addElement(imp1.getRoi());
		}
		imp1.deleteRoi();

		int[] vetx2 = new int[vetx1.length];
		int[] vety2 = new int[vetx1.length];
		for (int i1 = 0; i1 < vetx1.length; i1++) {
			vetx2[i1] = (int) Math.round(vetx1[i1]);
			vety2[i1] = (int) Math.round(vety1[i1]);
		}
		imp1.setRoi(new PointRoi(vetx2, vety2, vetx1.length));
		if (vetX.length == numRods1) {
			// if (!imp1.isVisible())
			// UtilAyv.showImageMaximized(imp1);
			IJ.wait(3000);
		}
		MyLog.waitHere();

		if (vetX.length != numRods1) {
			// if (cw2.running)
			// cw2.close();
			// if (imp3 != null)
			// imp3.flush();
			if (!imp1.isVisible())
				UtilAyv.showImageMaximized(imp1);
			imp1.getWindow().toFront();
			IJ.setTool("multipoint");
			MyLog.waitHere(listaMessaggi(7), debug, timeout);
			MyLog.waitHere("Sono state trovate "
					+ vetX.length
					+ " RODS anzichè "
					+ numRods1
					+ "\nCliccare sulle RODS non selezionate, per annullare le RODS sbagliate,\n"
					+ "cliccare sul puntino rosso mentre si tiene premuto ALT,\nalla fine premere OK");
		}

		//
		// if (demo || !silent) {
		// // if (cw2.running)
		// // cw2.close();
		// // if (imp3 != null)
		// // imp3.flush();
		// // imp3.close();
		// if (!imp1.isVisible())
		// UtilAyv.showImageMaximized(imp1);
		// imp1.getWindow().toFront();
		// if (demo)
		// MyLog.waitHere(listaMessaggi(4), debug, timeout);
		// else
		// IJ.wait(2000);
		// }
		//
		// if (vetx1.length == 0) {
		// MyLog.waitHere("lunghezza Results=0");
		// return null;
		// }
		//
		Polygon poli1 = imp1.getRoi().getPolygon();
		// int nPunti1=0;
		// if (poli1 == null) {
		// nPunti1 = 0;
		// } else {
		// nPunti1 = poli1.npoints;
		// }

		int[] xPoints = poli1.xpoints;
		int[] yPoints = poli1.ypoints;
		int[][] tabPunti = new int[xPoints.length][2];
		for (int i1 = 0; i1 < xPoints.length; i1++) {
			tabPunti[i1][0] = xPoints[i1];
			tabPunti[i1][1] = yPoints[i1];
		}
		over1.clear();
		return rtOut;
	}

	// public static ImagePlus strategiaGENERALE(ImagePlus imp1) {
	// if (imp1 == null) {
	// MyLog.waitHere();
	// return null;
	// }
	// // MyLog.waitHere("strategiaSIEMENS");
	// ImagePlus imp2 = p17rmn_.strategia3(imp1);
	//
	// // imp2.show();
	// // MyLog.waitHere();
	// ImagePlus imp3 = p17rmn_.strategia1(imp1);
	// // imp3.show();
	// // MyLog.waitHere();
	// ImagePlus imp4 = p17rmn_.combina(imp2, imp3);
	// // imp4.show();
	// // MyLog.waitHere();
	// return imp4;
	// }

	public static int[][] riordina(ResultsTable rt1) {
		rt1.show("Results");
		MyLog.waitHere();

		int xcol = rt1.getColumnIndex("XM");
		int ycol = rt1.getColumnIndex("YM");
		//
		double[] vetX = rt1.getColumnAsDoubles(xcol);
		double[] vetY = rt1.getColumnAsDoubles(ycol);

		double[][] matRods = new double[2][vetX.length];
		for (int i1 = 0; i1 < vetX.length; i1++) {
			matRods[0][i1] = vetX[i1];
			matRods[1][i1] = vetY[i1];
		}

		return null;
	}

	public static ImagePlus strategiaSIEMENS(ImagePlus imp1) {
		if (imp1 == null) {
			MyLog.waitHere();
			return null;
		}
		// MyLog.waitHere("strategiaSIEMENS");
		ImagePlus imp2 = p17rmn_.strategia0(imp1);
		// imp2.show();
		// MyLog.waitHere();
		ImagePlus imp3 = p17rmn_.strategia1(imp1);
		// imp3.show();
		// MyLog.waitHere();
		ImagePlus imp4 = p17rmn_.combina(imp2, imp3);
		// imp4.show();
		// MyLog.waitHere();
		return imp4;
	}

	public static ImagePlus strategiaHITACHI(ImagePlus imp1) {
		if (imp1 == null) {
			MyLog.waitHere();
			return null;
		}
		// MyLog.waitHere("strategiaHITACHI");
		// ImagePlus imp2 = p17rmn_.strategia4(imp1);
		// ImagePlus imp2 = p17rmn_.strategia5(imp1);
		ImagePlus imp2 = p17rmn_.strategia5(imp1);
		ImagePlus imp3 = p17rmn_.strategia1(imp1);
		ImagePlus imp4 = p17rmn_.combina(imp2, imp3);
		return imp4;
	}

	public static ImagePlus strategiaHITACHI2(ImagePlus imp1) {
		if (imp1 == null) {
			MyLog.waitHere();
			return null;
		}
		// MyLog.waitHere("strategiaHITACHI");
		// ImagePlus imp2 = p17rmn_.strategia4(imp1);
		// ImagePlus imp2 = p17rmn_.strategia5(imp1);
		ImagePlus imp2 = p17rmn_.strategia2(imp1);
		// imp2.show();
		// MyLog.waitHere("esterno");
		ImagePlus imp3 = p17rmn_.strategia1(imp1);
		// imp3.show("interno");
		// MyLog.waitHere();
		ImagePlus imp4 = p17rmn_.combina(imp2, imp3);
		// imp4.show("tutto");
		// MyLog.waitHere();
		return imp4;
	}

	public static ImagePlus strategiaGEMS(ImagePlus imp1) {
		if (imp1 == null) {
			MyLog.waitHere("imp1==null");
			return null;
		}
		// MyLog.waitHere("strategiaGEMS");
		ImagePlus imp2 = p17rmn_.strategia0(imp1);
		ImagePlus imp3 = p17rmn_.strategia1(imp1);
		ImagePlus imp4 = p17rmn_.combina(imp2, imp3);
		return imp4;
	}

	public static ImagePlus nuovaStrategia(ImagePlus imp1) {

		boolean noBlack = true;
		boolean noWhite = true;
		boolean doWhite = false;
		boolean doSet = false;
		boolean doLog = false;

		ImagePlus imp12 = MyAutoThreshold.threshold(imp1, "Huang", noBlack,
				noWhite, doWhite, doSet, doLog);
		// UtilAyv.showImageMaximized(imp12);
		// MyLog.waitHere();

		// ora analizzo l'immagine cercando il profilo tondo del fantoccio,
		int minSizePixels = 10000;
		int maxSizePixels = 300000;
		boolean excludeEdges = false;
		Roi roiCerchio = analisi0(imp12, minSizePixels, maxSizePixels,
				excludeEdges);
		if (roiCerchio == null) {
			MyLog.waitHere("roiCerchio==null");
			return null;
		}
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		ImageProcessor ip12 = imp12.getProcessor();
		ip12.invert();
		minSizePixels = 1000;
		maxSizePixels = 30000;
		excludeEdges = true;
		Roi roiQuadrato = analisi0(imp12, minSizePixels, maxSizePixels,
				excludeEdges);

		if (roiQuadrato == null) {
			MyLog.waitHere("roiQuadrato==null");
			return null;
		}
		ImagePlus imp112 = imp1.duplicate();
		ImageProcessor ip112 = imp112.getProcessor();
		//
		// Ora, sulla copia dell'immagine originale, annerisco l'esterno del
		// cerchio e l'interno del quadrato
		//
		//

		ip112.setColor(Color.BLACK);
		ip112.fillOutside(roiCerchio);
		ip112.fill(roiQuadrato);
		UtilAyv.showImageMaximized(imp112);

		short[] pixels = (short[]) ip112.getPixels();
		double[] dpixels = new double[pixels.length];
		for (int i1 = 0; i1 < pixels.length; i1++) {
			dpixels[i1] = (double) pixels[i1];
		}
		double maxThreshold = Tools.getMinMax(dpixels)[1];
		double minThreshold = Tools.getMinMax(dpixels)[0];
		minSizePixels = 50;
		maxSizePixels = 150;
		int lutUpdate = ImageProcessor.RED_LUT;
		int trovati = 0;
		do {
			maxThreshold = maxThreshold - 1;
			ImageProcessor ip1122 = imp112.getProcessor();
			ip1122.setThreshold(minThreshold, maxThreshold, lutUpdate);
			ip1122.setBinaryThreshold();
			imp112.updateAndRepaintWindow();
			ResultsTable rt1 = analisi1(imp112, minSizePixels, maxSizePixels,
					0, 1);
			if (rt1 == null)
				return null;
			trovati = rt1.getCounter();
			// MyLog.waitHere("threshold= " + minThreshold + " , " +
			// maxThreshold
			// + " trovati= " + trovati);
		} while (trovati < 32);
		MyLog.waitHere("TROVATI= " + trovati);

		return imp112;
	}

	public static int getMinValue(int[] array) {
		int minValue = array[0];
		for (int i = 1; i < array.length; i++) {
			if (array[i] < minValue) {
				minValue = array[i];
			}
		}
		return minValue;
	}

	public static int posMinValue(int[] array) {
		int minValue = array[0];
		int posMinValue = 0;
		for (int i = 1; i < array.length; i++) {
			if (array[i] < minValue) {
				minValue = array[i];
				posMinValue = i;
			}
		}
		return posMinValue;
	}

	/***
	 * La strategia 0 è dedicata al circolo esterno di rods nelle macchine
	 * Siemens
	 * 
	 * @param imp1
	 * @return
	 */
	public static ImagePlus strategia0(ImagePlus imp1) {

		boolean noBlack = true;
		boolean noWhite = true;
		boolean doWhite = false;
		boolean doSet = false;
		boolean doLog = false;

		// MyLog.waitHere("strategia 0");
		ImagePlus imp12 = MyAutoThreshold.threshold(imp1, "Mean", noBlack,
				noWhite, doWhite, doSet, doLog);
		// ora analizzo l'immagine cercando il profilo tondo del fantoccio,
		// riempirò l'esterno di nero
		int minSizePixels = 10000;
		int maxSizePixels = 300000;
		boolean excludeEdges = false;
		Roi roi0 = analisi0(imp12, minSizePixels, maxSizePixels, excludeEdges);

		if (roi0 == null) {
			UtilAyv.showImageMaximized(imp1);
			UtilAyv.showImageMaximized(imp12);
			MyLog.waitHere("roi0==null");
			return null;
		}
		ImagePlus imp2 = imp12.duplicate();
		ImageProcessor ip2 = imp2.getProcessor();
		ip2.setColor(Color.BLACK);
		ip2.fillOutside(roi0);
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		// riempirò l'interno di nero
		ip2.invert();
		minSizePixels = 1000;
		maxSizePixels = 30000;
		excludeEdges = true;
		Roi roi1 = analisi0(imp2, minSizePixels, maxSizePixels, excludeEdges);

		if (roi1 == null) {
			UtilAyv.showImageMaximized(imp1);
			UtilAyv.showImageMaximized(imp2);
			MyLog.waitHere("roi1==null");
			return null;
		}
		ImagePlus imp112 = imp2.duplicate();
		ImageProcessor ip112 = imp112.getProcessor();
		ip112.setColor(Color.WHITE);
		ip112.fill(roi1);
		IJ.run(imp112, "Invert", "");
		imp112.setTitle("ROI interne");

		return imp112;
	}

	public static Roi analisi0(ImagePlus imp1, int minSizePixel,
			int maxSizePixel, boolean excludeEdges) {
		int options = ParticleAnalyzer.SHOW_OUTLINES
				+ ParticleAnalyzer.ADD_TO_MANAGER;
		if (excludeEdges)
			options = ParticleAnalyzer.SHOW_OUTLINES
					+ ParticleAnalyzer.ADD_TO_MANAGER
					+ ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;

		int measurements = 0;
		ResultsTable rt0 = new ResultsTable();
		RoiManager rm0 = new RoiManager(false);
		ParticleAnalyzer pa1 = new ParticleAnalyzer(options, measurements, rt0,
				minSizePixel, maxSizePixel);
		ParticleAnalyzer.setRoiManager(rm0);
		pa1.setHideOutputImage(true);
		pa1.analyze(imp1);
		if (rm0.getCount() == 1) {
			Roi[] vetRoi = rm0.getRoisAsArray();
			Roi roi0 = vetRoi[0];
			return roi0;
		} else
			return null;
	}

	/***
	 * La strategia 1 è dedicata al circolo interno di rods nelle macchine
	 * Siemens
	 * 
	 * @param imp1
	 * @return
	 */

	public static ImagePlus strategia1(ImagePlus imp1) {

		boolean noBlack = true;
		boolean noWhite = false;
		boolean doWhite = true;
		boolean doSet = false;
		boolean doLog = false;
		ImagePlus imp12 = MyAutoThreshold.threshold(imp1, "Huang", noBlack,
				noWhite, doWhite, doSet, doLog);
		ImageProcessor ip12 = imp12.getProcessor();
		ip12.setColor(Color.BLACK);
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		// riempirò l'esterno di nero
		int minSizePixels = 100;
		int maxSizePixels = 10000;
		// importante exclude edges true!!!
		boolean excludeEdges = true;
		Roi roi1 = analisi0(imp12, minSizePixels, maxSizePixels, excludeEdges);
		if (roi1 == null) {
			// MyLog.waitHere("roi1==null");
			return null;
		}
		ip12.setColor(Color.BLACK);
		ip12.fillOutside(roi1);
		imp12.setTitle("ROI interne");
		return imp12;
	}

	public static ImagePlus combina(ImagePlus imp1, ImagePlus imp2) {
		if (imp1 == null)
			return null;
		if (imp2 == null)
			return null;
		ImageCalculator ic1 = new ImageCalculator();
		ImagePlus imp3 = ic1.run("OR create", imp1, imp2);
		IJ.run(imp3, "Invert", "");
		return imp3;
	}

	public static ImagePlus strategia2(ImagePlus imp1) {

		boolean noBlack = true;
		boolean noWhite = false;
		boolean doWhite = true;
		boolean doSet = false;
		boolean doLog = false;

		ImagePlus imp2 = MyAutoThreshold.threshold(imp1, "Moments", noBlack,
				noWhite, doWhite, doSet, doLog);
		IJ.run(imp2, "Invert", "");

		// ora analizzo l'immagine cercando il profilo tondo del fantoccio,
		// riempirò l'esterno di nero
		int minSizePixels = 10000;
		int maxSizePixels = 300000;
		boolean excludeEdges = false;

		Roi roi0 = analisi0(imp2, minSizePixels, maxSizePixels, excludeEdges);
		if (roi0 == null)
			return null;
		ImagePlus imp12 = imp2.duplicate();
		ImageProcessor ip12 = imp12.getProcessor();
		ip12.setColor(Color.BLACK);
		ip12.fillOutside(roi0);
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		// riempirò l'interno di nero
		ip12.invert();
		minSizePixels = 1000;
		maxSizePixels = 30000;
		excludeEdges = true;
		Roi roi1 = analisi0(imp12, minSizePixels, maxSizePixels, excludeEdges);
		if (roi1 == null)
			return null;
		ImagePlus imp112 = imp12.duplicate();
		ImageProcessor ip112 = imp112.getProcessor();
		ip112.setColor(Color.WHITE);
		ip112.fill(roi1);
		ip112.invert();
		imp112.setTitle("strategia2: Moments+Invert");
		return imp112;
	}

	/***
	 * Analizza i dati delle 32 rods forniti da automatiCRoiPreparation,
	 * prefiggendosi di identificare le diverse coppie di rods, in modo da
	 * poterle scrivere nelle posizioni corrette del report finale
	 * 
	 * @param imp1
	 * @param rt1
	 * @return
	 */
	public static ImagePlus filtroRisultati(ImagePlus imp1, ResultsTable rt1) {

		MyRats rat1 = new MyRats();
		ImagePlus imp2 = rat1.execute(imp1, null);
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		// riempirò l'esterno di nero prima di ricercare nuovamente i RATS. I
		// dati geometrici dell'inserto
		UtilAyv.showImageMaximized(imp2);
		// ricerca del quadrato interno
		int minSizePixels = 1000;
		int maxSizePixels = 30000;
		boolean excludeEdges = true;
		Roi roi1 = analisi0(imp2, minSizePixels, maxSizePixels, excludeEdges);
		if (roi1 == null) {
			MyLog.waitHere("roi1==null");
			return null;
		}
		ImagePlus imp11 = imp1.duplicate();

		imp11.setRoi(roi1);
		ImageProcessor ip11 = imp11.getProcessor();
		ip11.setColor(Color.WHITE);
		// ip11.setLineWidth(2);
		ip11.draw(roi1);
		ip11.fillOutside(roi1);
		ip11.setColor(Color.BLACK);
		ip11.fill(roi1);
		imp11.deleteRoi();
		UtilAyv.showImageMaximized(imp11);

		HarrisCornerDetector.Parameters params = new HarrisCornerDetector.Parameters();
		params.alpha = 0.2001;
		params.threshold = 25000;
		params.doCleanUp = true;
		// int nmax = 0;
		HarrisCornerDetector hcd = new HarrisCornerDetector(ip11, params);
		hcd.findCorners();
		PointRoi pr22 = hcd.returnCorners();

		imp11.setRoi(pr22);
		imp11.updateAndDraw();
		Roi roi11 = analizzaRisultati(imp11, pr22, 1, imp1, rt1);

		return imp11;
	}

	public static Roi analizzaRisultati(ImagePlus imp1, PointRoi pr1,
			int quadrant, ImagePlus imp2, ResultsTable rt1) {

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));

		Overlay over2 = new Overlay();
		imp2.setOverlay(over2);
		int dia1 = 8;

		// la pointRoi ricevuta ha i dati <ben disordinati>, per
		// mettere in corretto ordine i vertici utilizzo il getConvexHull
		Polygon p2 = pr1.getPolygon();
		PolygonRoi pol2 = new PolygonRoi(p2, PolygonRoi.POLYGON);
		PolygonRoi pol3 = new PolygonRoi(pol2.getConvexHull(),
				PolygonRoi.POLYGON);
		Polygon p3 = pol3.getPolygon();

		int[] vetxp = p3.xpoints;
		int[] vetyp = p3.ypoints;

		// trasformo le coordinate da integer a double
		double[] vetx = new double[vetxp.length];
		double[] vety = new double[vetxp.length];

		for (int i1 = 0; i1 < vetxp.length; i1++) {
			vetx[i1] = (double) vetxp[i1];
			vety[i1] = (double) vetyp[i1];
		}

		// marco i vertici ma solo per vedere se sono in ordine

		double lato = (new Line(vetxp[0], vetyp[0], vetxp[1], vetyp[1]))
				.getLength();

		if (true) {
			imp2.setRoi(new PointRoi(vetxp, vetyp, vetxp.length));
			imp2.getRoi().setStrokeColor(Color.white);
			over2.addElement(imp2.getRoi());

			for (int i1 = 0; i1 < vetxp.length; i1++) {
				imp2.setRoi(new OvalRoi(vetxp[i1] - dia1 / 2, vetyp[i1] - dia1
						/ 2, dia1, dia1));
				imp2.getRoi().setStrokeColor(c1[i1]);
				over2.addElement(imp2.getRoi());
				imp2.deleteRoi();
			}
		}

		// prolungo i lati del quadrato fino ai bordi dell'immagine, creo una
		// PolygonRoi
		// double[] cross1 = ImageUtils.crossingFrame(vetx[0], vety[0], vetx[1],
		// vety[1], imp1.getWidth(), imp1.getHeight());
		// Line linea1 = new Line(cross1[0], cross1[1], cross1[2], cross1[3]);
		// imp2.setRoi(linea1);
		// imp2.updateAndDraw();
		// imp2.getRoi().setStrokeColor(Color.red);
		// over2.addElement(imp2.getRoi());

		// double[] cross2 = ImageUtils.crossingFrame(vetx[2], vety[2], vetx[3],
		// vety[3], imp1.getWidth(), imp1.getHeight());
		// Line linea2 = new Line(cross2[0], cross2[1], cross2[2], cross2[3]);
		// imp2.setRoi(linea2);
		// imp2.updateAndDraw();
		// imp2.getRoi().setStrokeColor(Color.red);
		// over2.addElement(imp2.getRoi());

		// double[] cross3 = ImageUtils.crossingFrame(vetx[1], vety[1], vetx[2],
		// vety[2], imp1.getWidth(), imp1.getHeight());
		// Line linea3 = new Line(cross3[0], cross3[1], cross3[2], cross3[3]);
		// imp2.setRoi(linea3);
		// imp2.updateAndDraw();
		// imp2.getRoi().setStrokeColor(Color.green);
		// over2.addElement(imp2.getRoi());

		// double[] cross4 = ImageUtils.crossingFrame(vetx[0], vety[0], vetx[3],
		// vety[3], imp1.getWidth(), imp1.getHeight());
		// Line linea4 = new Line(cross4[0], cross4[1], cross4[2], cross4[3]);
		// imp2.setRoi(linea4);
		// imp2.updateAndDraw();
		// imp2.getRoi().setStrokeColor(Color.green);
		// over2.addElement(imp2.getRoi());
		// imp2.deleteRoi();

		// identifico il primo cerchio

		imp2.setRoi(new PointRoi(vetxp, vetyp, vetxp.length));

		MyCircleDetector.fitCircle(imp2);
		imp2.getRoi().setStrokeColor(Color.green);
		over2.addElement(imp2.getRoi());

		Rectangle boundingRectangle2 = imp2.getProcessor().getRoi();
		// imp2.setRoi(boundingRectangle2);
		// imp2.getRoi().setStrokeColor(Color.blue);
		// over2.addElement(imp2.getRoi());

		int diamRoi = (int) boundingRectangle2.width;
		int xRoi = boundingRectangle2.x + boundingRectangle2.width / 2;
		int yRoi = boundingRectangle2.y + boundingRectangle2.height / 2;
		// xRoi += 1;
		// yRoi += 1;
		//

		imp2.setRoi(new OvalRoi(xRoi - dia1 / 2, yRoi - dia1 / 2, dia1, dia1));
		imp2.getRoi().setStrokeColor(Color.red);
		over2.addElement(imp2.getRoi());
		imp2.deleteRoi();

		// ora disegno un cerchio di diametro doppio

		int diamRoi3 = (int) Math.round(diamRoi * 2.5);

		imp2.setRoi(new OvalRoi(xRoi - diamRoi3 / 2, yRoi - diamRoi3 / 2,
				diamRoi3, diamRoi3));
		imp2.getRoi().setStrokeColor(Color.red);
		over2.addElement(imp2.getRoi());
		imp2.deleteRoi();

		// ================================
		// penzo ad un altra strategia: faccio le bisettrici dei lati del
		// quadrato e le diagonali, prolungandole fino al bordo immagine.
		// ================================

		double alfa1 = (new Line(vetx[0], vety[0], vetx[1], vety[1]).getAngle());

		double lato1 = (lato / 2 * Math.cos(alfa1));

		// la prima bisettrice dovrebbe avere le seguenti coordinate

		double halfX1 = 0;
		if (vetx[0] <= vetx[1])
			halfX1 = (vetx[1] - vetx[0]) / 2 + vetx[0];
		else
			halfX1 = (vetx[0] - vetx[1]) / 2 + vetx[1];
		double halfY1 = 0;
		if (vety[0] <= vety[1])
			halfY1 = (vety[1] - vety[0]) / 2 + vety[0];
		else
			halfY1 = (vety[0] - vety[1]) / 2 + vety[1];

		double halfX2 = 0;
		if (vetx[2] <= vetx[3])
			halfX2 = (vetx[3] - vetx[2]) / 2 + vetx[2];
		else
			halfX2 = (vetx[2] - vetx[3]) / 2 + vetx[3];

		double halfY2 = 0;
		if (vety[2] <= vety[3])
			halfY2 = (vety[3] - vety[2]) / 2 + vety[2];
		else
			halfY2 = (vety[2] - vety[3]) / 2 + vety[3];

		IJ.log("lato/2= " + lato / 2 + " lato1= " + lato1);

		double[] cross5 = ImageUtils.crossingFrame(halfX1, halfY1, halfX2,
				halfY2, imp1.getWidth(), imp1.getHeight());
		Line linea5 = new Line(cross5[0], cross5[1], cross5[2], cross5[3]);
		imp2.setRoi(linea5);
		imp2.updateAndDraw();
		imp2.getRoi().setStrokeColor(c1[0]);
		over2.addElement(imp2.getRoi());
		imp2.deleteRoi();
		imp2.getWindow().toFront();
		
		
		// la bisettrice opposta sarà:
		if (vetx[0] <= vetx[3])
			halfX1 = (vetx[3] - vetx[0]) / 2 + vetx[0];
		else
			halfX1 = (vetx[0] - vetx[3]) / 2 + vetx[3];
		if (vety[0] <= vety[3])
			halfY1 = (vety[3] - vety[0]) / 2 + vety[0];
		else
			halfY1 = (vety[0] - vety[3]) / 2 + vety[3];

		if (vetx[1] <= vetx[2])
			halfX2 = (vetx[2] - vetx[1]) / 2 + vetx[1];
		else
			halfX2 = (vetx[1] - vetx[2]) / 2 + vetx[2];

		if (vety[1] <= vety[2])
			halfY2 = (vety[2] - vety[1]) / 2 + vety[1];
		else
			halfY2 = (vety[1] - vety[2]) / 2 + vety[2];
		
		double[] cross6 = ImageUtils.crossingFrame(halfX1, halfY1, halfX2,
				halfY2, imp1.getWidth(), imp1.getHeight());
		Line linea6 = new Line(cross6[0], cross6[1], cross6[2], cross6[3]);
		imp2.setRoi(linea6);
		imp2.updateAndDraw();
		imp2.getRoi().setStrokeColor(c1[1]);
		over2.addElement(imp2.getRoi());

		MyLog.waitHere();

		// //============================ prima strategia testata, creazione di
		// poligoni con lato parallelo ai lati del quadrato
		// double lato1 = 1.3 * (new Line(vetx[0], vety[0], vetx[1], vety[1]))
		// .getLength();
		// double alfa1 = (new Line(vetx[0], vety[0], vetx[1],
		// vety[1]).getAngle());
		//
		// double punto1[] = MyGeometry.traslateRotatePoint(vetx[0], vety[0], 0,
		// lato1, alfa1);
		// double punto2[] = MyGeometry.traslateRotatePoint(vetx[1], vety[1], 0,
		// lato1, alfa1);
		//
		// float[] vet2x = new float[4];
		// float[] vet2y = new float[4];
		// vet2x[0] = (float) vetx[1];
		// vet2x[1] = (float) vetx[0];
		// vet2x[2] = (float) punto1[0];
		// vet2x[3] = (float) punto2[0];
		//
		// vet2y[0] = (float) vety[1];
		// vet2y[1] = (float) vety[0];
		// vet2y[2] = (float) punto1[1];
		// vet2y[3] = (float) punto2[1];
		//
		// imp2.setRoi(new PolygonRoi(vet2x, vet2y, Roi.POLYGON));
		// imp2.updateAndDraw();
		// imp2.getRoi().setStrokeColor(Color.green);
		// over2.addElement(imp2.getRoi());
		//
		// imp2.setRoi(new OvalRoi(vetx[1] - dia1 / 2, vety[1] - dia1 / 2, dia1,
		// dia1));
		// imp2.getRoi().setStrokeColor(Color.green);
		// over2.addElement(imp2.getRoi());
		// imp2.setRoi(new OvalRoi(vetx[3] - dia1 / 2, vety[3] - dia1 / 2, dia1,
		// dia1));
		// imp2.getRoi().setStrokeColor(Color.green);
		// over2.addElement(imp2.getRoi());
		//
		// double punto3[] = MyGeometry.traslateRotatePoint(vetx[1], vety[1],
		// lato1, 0, alfa1);
		// double punto4[] = MyGeometry.traslateRotatePoint(vetx[3], vety[3],
		// lato1, 0, alfa1);
		//
		// MyLog.logVector(vetx, "vetx");
		// MyLog.logVector(vety, "vety");
		//
		// float[] vet3x = new float[4];
		// float[] vet3y = new float[4];
		// vet3x[0] = (float) vetx[3];
		// vet3x[1] = (float) vetx[1];
		// vet3x[2] = (float) punto3[0];
		// vet3x[3] = (float) punto4[0];
		//
		// vet3y[0] = (float) vety[3];
		// vet3y[1] = (float) vety[1];
		// vet3y[2] = (float) punto3[1];
		// vet3y[3] = (float) punto4[1];
		//
		// imp2.setRoi(new PolygonRoi(vet3x, vet3y, Roi.POLYGON));
		// imp2.updateAndDraw();
		// imp2.getRoi().setStrokeColor(Color.red);
		// over2.addElement(imp2.getRoi());

		// analizzo la ResultsTable
		Calibration cal = imp2.getCalibration();

		int xcol = rt1.getColumnIndex("XM");
		int ycol = rt1.getColumnIndex("YM");
		//
		double[] vetX = rt1.getColumnAsDoubles(xcol);
		// MyLog.logVector(vetX, "vetX");
		double[] vetY = rt1.getColumnAsDoubles(ycol);
		// MyLog.logVector(vetX, "vetX");

		double[] vetX3 = new double[vetX.length];
		double[] vetY3 = new double[vetY.length];
		for (int i1 = 0; i1 < vetX3.length; i1++) {
			vetX3[i1] = vetX[i1];
			vetY3[i1] = vetY[i1];
		}

		double[] vetX2 = new double[vetX3.length];
		double[] vetY2 = new double[vetY3.length];
		for (int i1 = 0; i1 < vetX3.length; i1++) {
			vetX2[i1] = cal.getRawX(vetX3[i1]);
			vetY2[i1] = cal.getRawY(vetY3[i1]);
		}

		float[] vetXf = UtilAyv.toFloat(vetX2);
		float[] vetYf = UtilAyv.toFloat(vetY2);

		imp2.setRoi(new PointRoi(vetXf, vetYf, vetXf.length));
		imp2.getRoi().setStrokeColor(Color.yellow);
		over2.addElement(imp2.getRoi());
		imp2.deleteRoi();

		double[] vetDist = new double[vetX.length];
		double[] vetOrder = new double[vetX.length];

		for (int i1 = 0; i1 < vetX.length; i1++) {
			vetDist[i1] = MyGeometry.pointToLineDistance(cal.getX(cross5[0]),
					cal.getY(cross5[1]), cal.getX(cross5[2]),
					cal.getY(cross5[3]), vetX[i1], vetY[i1]);
			vetOrder[i1] = (double) i1;
		}

		UtilAyv.minsort(vetDist, vetOrder);

		for (int i1 = 0; i1 < vetX.length; i1++) {
			IJ.log("" + vetOrder[i1] + "  " + vetDist[i1]);
		}
		MyLog.logVector(vetDist, "vetDist");
		MyLog.waitHere("le prime 8 distanze sono quelle bbone");
		// metto le coordinate delle prime 8 distanze in due vettori. Ora ne
		// calcolo la proiezione sulla retta e andrò a vedere la distanza della
		// proiezione dal punto di partenza. Questo mi dovrebbe identificare le
		// diverse rod e la loro appartenenza ad una determinata coppia. ABBIAMO
		// QUASI FINITO

		double[] newX = new double[8];
		double[] newY = new double[8];
		double[] newD = new double[8];

		for (int i1 = 0; i1 < 8; i1++) {
			newX[i1] = vetX[(int) vetOrder[i1]];
			newY[i1] = vetY[(int) vetOrder[i1]];

			Point p1 = new Point((int) Math.round(newX[i1]),
					(int) Math.round(newY[i1]));
			Point a1 = new Point((int) Math.round(cross5[0]),
					(int) Math.round(cross5[1]));
			Point a2 = new Point((int) Math.round(cross5[2]),
					(int) Math.round(cross5[3]));

			Point p4 = MyGeometry.getProjectedPointOnLineFast(a1, a2, p1);
			double d1 = MyGeometry.pointsDistance(a1, p4);
			newD[i1] = d1;
		}

		UtilAyv.minsort(newD, newX, newY);
		int i2 = 0;
		for (int i1 = 0; i1 < 8; i1++) {
			i2 = i1 / 2;
			imp2.setRoi(new PointRoi(cal.getRawX(newX[i1]), cal
					.getRawY(newY[i1])));
			imp2.getRoi().setStrokeColor(c1[i2]);
			over2.addElement(imp2.getRoi());
			imp2.deleteRoi();
		}

		imp2.getWindow().toFront();
		MyLog.waitHere();

		return null;
	}

	public static ImagePlus[] strategiaGENERALE(ImagePlus imp1) {

		// Questa potrebbe essere la strategia generale. Nel proimo passaggio
		// viene effettuato il threshold automatico con RATS. Otterrò il cerchio
		// esterno, le RODS esterne ed il quadrato interno.

		MyRats rat1 = new MyRats();
		ImagePlus imp2 = rat1.execute(imp1, null);
		ImageProcessor ip2 = imp2.getProcessor();
		ip2.invert();
		// ora analizzo l'immagine cercando il profilo tondo del fantoccio,
		// riempirò l'esterno di nero
		int minSizePixels = 10000;
		int maxSizePixels = 300000;
		boolean excludeEdges = false;
		Roi roi0 = analisi0(imp2, minSizePixels, maxSizePixels, excludeEdges);
		if (roi0 == null) {
			return null;
		}
		ip2.setColor(Color.BLACK);
		ip2.fillOutside(roi0);
		ip2.invert();
		imp2.updateAndDraw();
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		// riempirò l'interno di bianco
		minSizePixels = 1000;
		maxSizePixels = 30000;
		excludeEdges = true;
		Roi roi1 = analisi0(imp2, minSizePixels, maxSizePixels, excludeEdges);
		if (roi1 == null)
			return null;
		ip2.setColor(Color.WHITE);
		ip2.fill(roi1);
		// ip2.invert();
		imp2.updateAndDraw();
		imp2.copyScale(imp1);
		// UtilAyv.showImageMaximized(imp2);
		// MyLog.waitHere("ESTERNO");
		// ===========================================================================
		// faccio un duplicato di imp1, a questo applico la roi quadrata e
		// cancello tutto l'esterno
		ImagePlus imp11 = imp1.duplicate();
		imp11.setRoi(roi1);
		ImageProcessor ip11 = imp11.getProcessor();
		ip11.setColor(Color.BLACK);
		ip11.setLineWidth(2);
		ip11.draw(roi1);
		ip11.fillOutside(roi1);
		imp11.updateAndDraw();
		// UtilAyv.showImageMaximized(imp11);
		// MyLog.waitHere();
		ImagePlus imp3 = rat1.execute(imp11, null);
		ImageProcessor ip3 = imp3.getProcessor();
		ip3.invert();
		imp3.updateAndDraw();
		imp3.copyScale(imp1);
		// UtilAyv.showImageMaximized(imp3);
		// MyLog.waitHere("INTERNO");
		// ===========================================================================
		ImagePlus[] vetImp = new ImagePlus[2];
		vetImp[0] = imp2;
		vetImp[1] = imp3;

		return vetImp;
	}

	public static ImagePlus strategia4(ImagePlus imp1) {

		boolean noBlack = false;
		boolean noWhite = false;
		boolean doWhite = false;
		boolean doSet = false;
		boolean doLog = false;

		// MyLog.waitHere("strategia 4");
		ImagePlus imp11 = imp1.duplicate();
		IJ.run(imp11, "Invert", "");
		ImagePlus imp2 = MyAutoThreshold.threshold(imp11, "Li", noBlack,
				noWhite, doWhite, doSet, doLog);
		IJ.run(imp2, "Invert", "");
		imp2.setTitle("strategia1: Invert+Li+Invert");
		// ora analizzo l'immagine cercando il profilo tondo del fantoccio,
		// riempirò l'esterno di nero
		int minSizePixels = 10000;
		int maxSizePixels = 300000;
		boolean excludeEdges = false;

		Roi roi0 = analisi0(imp2, minSizePixels, maxSizePixels, excludeEdges);
		if (roi0 == null)
			return null;
		ImagePlus imp12 = imp2.duplicate();
		ImageProcessor ip12 = imp12.getProcessor();
		ip12.setColor(Color.BLACK);
		ip12.fillOutside(roi0);
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		// riempirò l'interno di nero
		ip12.invert();
		minSizePixels = 1000;
		maxSizePixels = 30000;
		excludeEdges = true;
		Roi roi1 = analisi0(imp12, minSizePixels, maxSizePixels, excludeEdges);
		if (roi1 == null)
			return null;
		ImagePlus imp112 = imp12.duplicate();
		ImageProcessor ip112 = imp112.getProcessor();
		ip112.setColor(Color.WHITE);
		ip112.fill(roi1);
		IJ.run(imp112, "Invert", "");
		return imp112;
	}

	public static ResultsTable analisi1(ImagePlus imp1, double minSize,
			double maxSize, double minCirc, double maxCirc) {
		int options = ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
		int measurements = Measurements.CENTER_OF_MASS + Measurements.AREA;

		ResultsTable rt0 = new ResultsTable();

		ParticleAnalyzer pa1 = new ParticleAnalyzer(options, measurements, rt0,
				minSize, maxSize, minCirc, maxCirc);
		pa1.setHideOutputImage(true);
		pa1.analyze(imp1);
		return rt0;

	}

	/***
	 * La strategia 0 è dedicata al circolo esterno di rods nelle macchine
	 * HITACHI
	 * 
	 * @param imp1
	 * @return
	 */
	public static ImagePlus strategia5(ImagePlus imp1) {

		boolean noBlack = true;
		boolean noWhite = true;
		boolean doWhite = false;
		boolean doSet = false;
		boolean doLog = false;

		// MyLog.waitHere("strategia 5");
		ImagePlus imp12 = MyAutoThreshold.threshold(imp1, "RenyiEntropy",
				noBlack, noWhite, doWhite, doSet, doLog);
		// ora analizzo l'immagine cercando il profilo tondo del fantoccio,
		// riempirò l'esterno di nero
		// UtilAyv.showImageMaximized(imp12);
		// MyLog.waitHere();
		int minSizePixels = 10000;
		int maxSizePixels = 300000;
		boolean excludeEdges = false;
		Roi roi0 = analisi0(imp12, minSizePixels, maxSizePixels, excludeEdges);

		if (roi0 == null) {
			// UtilAyv.showImageMaximized(imp1);
			// UtilAyv.showImageMaximized(imp12);
			// MyLog.waitHere("roi0==null");
			return null;
		}
		ImagePlus imp2 = imp12.duplicate();
		ImageProcessor ip2 = imp2.getProcessor();
		ip2.setColor(Color.BLACK);
		ip2.fillOutside(roi0);
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		// riempirò l'interno di nero
		ip2.invert();
		minSizePixels = 1000;
		maxSizePixels = 30000;
		excludeEdges = true;
		Roi roi1 = analisi0(imp2, minSizePixels, maxSizePixels, excludeEdges);

		if (roi1 == null) {
			// UtilAyv.showImageMaximized(imp1);
			// UtilAyv.showImageMaximized(imp2);
			// MyLog.waitHere("roi1==null");
			return null;
		}
		ImagePlus imp112 = imp2.duplicate();
		ImageProcessor ip112 = imp112.getProcessor();
		ip112.setColor(Color.WHITE);
		ip112.fill(roi1);
		IJ.run(imp112, "Invert", "");
		imp112.setTitle("ROI interne");

		return imp112;
	}

	public static ResultsTable analisi(ImagePlus imp1, boolean verbose,
			int timeout, int numRoi) {

		if (imp1 == null)
			return null;
		int options = ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES
				+ ParticleAnalyzer.SHOW_OUTLINES;
		int measurements = Measurements.CENTER_OF_MASS + Measurements.AREA;
		double minSize = 50;
		double maxSize = 100;
		double minCirc = 0.5;
		double maxCirc = 1;
		ResultsTable rt0 = new ResultsTable();
		ParticleAnalyzer pa1 = null;
		do {
			rt0.reset();
			pa1 = new ParticleAnalyzer(options, measurements, rt0, minSize,
					maxSize, minCirc, maxCirc);
			pa1.setHideOutputImage(true);
			boolean ok = pa1.analyze(imp1);
			minSize--;
			// if (minSize <= 3) {
			// ImagePlus imp100 = pa1.getOutputImage();
			// UtilAyv.showImageMaximized(imp100);
			// MyLog.waitHere("GULP");
			// }
		} while ((rt0.getCounter() < numRoi) && (minSize > 3));

		if (verbose) {
			ImagePlus imp100 = pa1.getOutputImage();
			// rt0.show("Results");
			UtilAyv.showImageMaximized(imp100);
			MyLog.waitHere("Oggetti trovati", debug, timeout);
			imp100.close();
		}
		return rt0;

	}

	public void overlayRodNumbers(ImagePlus imp1, int diamRoi, boolean verbose) {

		Roi roi2 = imp1.getRoi();
		Rectangle rec2 = roi2.getBounds();
		imp1.setRoi(new OvalRoi(0, 0, 0, 0));
		double bx = rec2.getX();
		double by = rec2.getY();
		double bw = rec2.getWidth() / imp1.getWidth();
		CustomCanvasGeneric ccg1 = new CustomCanvasGeneric(imp1);
		double[] cx1 = { 245, 105, 245, 380, 175, 310, 75, 160, 335, 425, 175,
				310, 105, 245, 380, 245 };
		double[] cy1 = { 40, 95, 130, 95, 160, 160, 230, 230, 230, 230, 305,
				305, 370, 330, 370, 430 };
		double[] cx2 = { 250, 270, 250, 230 };
		double[] cy2 = { 240, 260, 280, 260 };
		ccg1.setOffset(bx, by, bw);
		ccg1.setPosition1(cx1, cy1);
		ccg1.setColor1(Color.red);
		ccg1.setPosition2(cx2, cy2);
		ccg1.setColor2(Color.green);
		if (verbose) {
			ImageWindow iw1 = new ImageWindow(imp1, ccg1);
			iw1.maximize();
		}
	}

	void selfTestAyv() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			switch (userSelection2) {
			case 1: {
				// GE
				String home1 = findTestImages();
				String path1 = home1 + "/HWSA2_testP7";
				int[] vetX = MyConst.P7_X_POINTS_TESTGE;
				int[] vetY = MyConst.P7_Y_POINTS_TESTGE;
				boolean ok = testExcecution(path1, vetX, vetY, -10, -17, true);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				return;
			}
			case 2:
				// Siemens
				String home1 = findTestImages();
				String path1 = home1 + "/HWSA_testP7";
				int[] vetX = MyConst.P7_X_POINTS_TESTSIEMENS;
				int[] vetY = MyConst.P7_Y_POINTS_TESTSIEMENS;
				boolean ok = testExcecution(path1, vetX, vetY, 0, 20, true);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				return;
			}
		}
		UtilAyv.noTest2();
	}

	void selfTestSilent() {
		String[] list = { "HWSA2_testP7" };
		String[] path2 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path2[0];

		int[] vetX = MyConst.P7_X_POINTS_TESTSIEMENS;
		int[] vetY = MyConst.P7_Y_POINTS_TESTSIEMENS;
		boolean ok = testExcecution(path1, vetX, vetY, 0, 0, false);
		if (ok) {
			IJ.log("Il test di p7rmn_ WARP è stato SUPERATO");
		} else {
			IJ.log("Il test di p7rmn_ WARP evidenzia degli ERRORI");
		}
		return;
	}

	boolean testExcecution(String path1, int[] vetX, int[] vetY, int offX,
			int offY, boolean verbose) {
		ImagePlus imp1 = null;
		if (verbose) {
			imp1 = UtilAyv.openImageMaximized(path1);
		} else {
			imp1 = UtilAyv.openImageNoDisplay(path1, false);
		}
		double dimPixel2 = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));
		double diamRoi1 = (double) MyConst.P7_DIAM_ROI / dimPixel2;
		int diamRoi = (int) diamRoi1;
		boolean circular = true;
		UtilAyv.presetRoi(imp1, diamRoi, offX, offY, circular);
		if (verbose)
			IJ.wait(500);
		overlayRodNumbers(imp1, diamRoi, verbose);
		Polygon poli1 = UtilAyv.clickSimulation(imp1, vetX, vetY);
		boolean ok = UtilAyv.verifyResults2(poli1.xpoints, poli1.ypoints, vetX,
				vetY, "della ROD ");
		return ok;
	}

	/**
	 * genera una directory temporanea e vi estrae le immagini di test da
	 * test2.jar
	 * 
	 * @return home1 path della directory temporanea con le immagini di test
	 */
	private String findTestImages() {
		InputOutput io = new InputOutput();
		io.extractFromJAR(MyConst.TEST_FILE, "HWSA_testP7", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "HWSA2_testP7", "./Test2/");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY)
				.getPath();
		return (home1);
	}

	private static void msgPositionRoi() {
		ButtonMessages.ModelessMsg("Posizionare la ROI  e premere CONTINUA",
				"CONTINUA");
	}

	private static void msgRedo(int nPunti) {
		IJ.showMessage("--- A T T E N Z I O N E ---",
				"Sono stati selezionati solo " + nPunti
						+ " anzichè 36  punti,\n--- R I F A R E ---");
	}

	/**
	 * Siemens test image expected results
	 * 
	 * @return
	 */
	/**
	 * Siemens test image expected results
	 * 
	 * @return
	 */
	static int[][] referenceSiemens() {

		int[][] vetReference = { { 3, 0 }, { 257, 86 }, { 252, 105 },
				{ 384, 138 }, { 123, 139 }, { 127, 156 }, { 380, 157 },
				{ 254, 177 }, { 253, 196 }, { 189, 203 }, { 317, 203 },
				{ 189, 222 }, { 318, 222 }, { 90, 267 }, { 171, 267 },
				{ 334, 267 }, { 417, 267 }, { 88, 286 }, { 170, 287 },
				{ 335, 286 }, { 417, 286 }, { 315, 331 }, { 187, 332 },
				{ 189, 351 }, { 317, 351 }, { 251, 358 }, { 252, 377 },
				{ 124, 397 }, { 381, 397 }, { 380, 414 }, { 124, 416 },
				{ 251, 448 }, { 252, 467 }, { 256, 247 }, { 223, 279 },
				{ 288, 282 }, { 254, 313 }, { 0, 0 } };

		return vetReference;
	}

	/**
	 * Qui sono raggruppati tutti i messaggi del plugin, in questo modo è
	 * facilitata la eventuale modifica / traduzione dei messaggi.
	 * 
	 * @param select
	 * @return
	 */
	public static String listaMessaggi(int select) {
		String[] lista = new String[100];
		// ---------+-----------------------------------------------------------+
		lista[0] = "L'immagine in input viene processata con AutoThreshold.threshold+Median";
		lista[1] = "L'immagine binaria viene invertita";
		lista[2] = "Viene utilizzato Analyze Particles (size=50-1000 circularity=0.1-1.00)\n "
				+ "per identificare i gel e misurarne misurare i Bounding Rectangles";
		lista[3] = "Misurando i BoundingRectangles otteniamo questi risultati";
		lista[4] = "Posizione ROI in verde";
		lista[5] = "L'immagine in input viene processata con AutoThreshold.threshold+Huang";
		lista[6] = "Numero nuovi oggetti localizzati= ";
		lista[7] = "Numero ROI rilevate errato, posizioni ROI in verde";

		// ---------+-----------------------------------------------------------+
		String out = lista[select];
		return out;
	}

}
