
package contMensili;
//
//import ij.IJ;
//import ij.ImagePlus;
//import ij.Prefs;
//import ij.gui.ImageCanvas;
//import ij.gui.ImageWindow;
//import ij.gui.Line;
//import ij.gui.Roi;
//import ij.measure.Measurements;
//import ij.measure.ResultsTable;
//import ij.plugin.PlugIn;
//import ij.plugin.filter.Analyzer;
//
//import java.util.StringTokenizer;
//
//import utils.AboutBox;
//import utils.CustomCanvasMTF;
//import utils.TableSequence;
//import utils.Singleton;
//import utils.UtilAyv;
//import utils.ButtonMessages;
//import utils.ReadDicom;
//import utils.InputOutput;
//import utils.ReportStandardInfo;
//
//
///*
// * Copyright (C) 2007 Alberto Duina, SPEDALI CIVILI DI BRESCIA, Brescia ITALY
// *
// * This program is free software; you can redistribute it and/or
// * modify it under the terms of the GNU General Public License
// * as published by the Free Software Foundation; either version 2
// * of the License, or (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with this program; if not, write to the Free Software
// * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
// */
//
///**
// * Analizza la MTF, in base al posizionamento di un segmento vengono inserite
// * nel RoiManager Roi posizionate su tutte le line pairs e su acqua e plexiglas.
// * Su queste Roi vengono eseguiti i calcoli.
// * 
// * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
// * directory plugins
// * 
// * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
// *         Sanitaria
// * 
// */
//
//public class p4rmn_ implements PlugIn, Measurements {
//
//	public static String VERSION = "p4_rmn_v4.12_05feb09_";
//
//	private static String TYPE = " >> CONTROLLO MTF_____________________";
//
//	// ---------------------------"01234567890123456789012345678901234567890"
//
//	private static final double X_START_REFLINE_TESTSIEMENS = 14.6;
//
//	private static final double Y_START_REFLINE_TESTSIEMENS = 31.7;
//
//	private static final double X_END_REFLINE_TESTSIEMENS = 40.1;
//
//	private static final double Y_END_REFLINE_TESTSIEMENS = 58.5;
//	
//
//	private static final double X_START_REFLINE_TESTGE = 13.1;
//
//	private static final double Y_START_REFLINE_TESTGE = 32.3;
//
//	private static final double X_END_REFLINE_TESTGE = 43.2;
//
//	private static final double Y_END_REFLINE_TESTGE = 53.6;
//
//	private final double[] X_ROI_POSITION = { 7, -18, 29, -10, 113, 70, 70 };
//
//	private final double[] Y_ROI_POSITION = { 57, 87, 62, 34, 64, 25, 63 };
//
//	private final double[] DIA_ROI = { 15, 12, 8, 5, 3, 10, 10 };
//
//	private final String TEST_DIRECTORY = "/test2/";
//
//	private final String TEST_FILE = "test2.jar";
//
//	private final String SEQUENZE_FILE = "iw2ayv.txt";
//
//	private final String XLS_FILE = "Result1.xls";
//
//	private final String CODE_FILE = "/codici.txt";
//
//	private final int TOKENS4 = 4;
//
//	private final int TOKENS1 = 1;
//
//	private final int MEAN = 2; // passato a ImageJ
//
//	private final int STD_DEV = 4; // passato a ImageJ
//
//	private final String DICOM_PIXEL_SPACING = "0028,0030";
//
//	// private final String DICOM_ROWS = "0028,0010";
//
//	// private static int MATRIX512 = 512;
//
//	// private static int MATRIX256 = 256;
//
//	public static final int ABORT = 1;
//
//	/**
//	 * directory dati, dove vengono memorizzati ayv.txt e Results1.xls
//	 */
//	public String fileDir = Prefs.get("prefer.string1", "none");
//
//	/**
//	 * tabella coi dati di ayv.txt (generati da Sequenze)
//	 */
//	public String[][] strRiga3;
//
//	/**
//	 * tabella coi dati di codici.txt
//	 */
//	public String[][] tabl;
//
//	/**
//	 * true se viene selezionato PASSO
//	 */
//	public boolean bstep = false;
//
//	public void run(String args) {
//
//		int userSelection1 = 0;
//		int userSelection2 = 0;
//		int userSelection3 = 0;
//
//		int riga1 = 0;
//		int visualResolution = 0;
//
//		boolean accetta = false;
//
//		CustomCanvasMTF cc4;
//
//		String fileName = SEQUENZE_FILE;
//		String fileXls = XLS_FILE;
//		String path1 = "";
//
//		boolean autoCalled = false;
//		boolean manualCalled = false;
//
//		UtilAyv utils = new UtilAyv();
//		ReadDicom rd = new ReadDicom();
//		ButtonMessages bm = new ButtonMessages();
//		InputOutput io = new InputOutput();
//		ReportStandardInfo rsi = new ReportStandardInfo();
//		
//		
//		
//		
//		
//		
//		tabl = io.readFile1(CODE_FILE, TOKENS4);
//
//		StringTokenizer st = new StringTokenizer(args, "#");
//		int nTokens = st.countTokens();
//		if (nTokens > 0) {
//			autoCalled = true;
//			manualCalled = false;
//		} else {
//			autoCalled = false;
//			manualCalled = true;
//		}
//
//		boolean selftest = false;
//		bstep = false;
//		boolean testSiemens = false;
//		boolean testGe = false;
//		double xStartRefline = 0;
//		double yStartRefline = 0;
//		double xEndRefline = 0;
//		double yEndRefline = 0;
//		double xStartReflineUser = 0;
//		double yStartReflineUser = 0;
//		double xEndReflineUser = 0;
//		double yEndReflineUser = 0;
//
//		AboutBox ab = new AboutBox();
//		boolean retry = false;
//		if (manualCalled) {
//			retry = false;
//			do {
//				userSelection1 = utils.userSelectionManual(VERSION, TYPE);
//				switch (userSelection1) {
//				case ABORT:
//					ab.close();
//					retry = false;
//					return;
//				case 2:
//					new AboutBox().about("Controllo MTF", this.getClass());
//					retry = true;
//					break;
//				case 3:
//					selftest = true;
//					retry = false;
//					break;
//				case 4:
//					bstep = true;
//					retry = false;
//					break;
//				case 5:
//					retry = false;
//					break;
//				}
//			} while (retry);
//			ab.close();
//
//			if (selftest) {
//				if (!io.checkJar("test2.jar")) {
//					utils.noTest2();
//					return;
//				} else {
//
//					userSelection2 = utils.siemensGe();
//					switch (userSelection2) {
//					case 1:
//						testSiemens = false;
//						testGe = true;
//						break;
//					case 2:
//						testSiemens = true;
//						testGe = false;
//						break;
//					}
//				}
//
//				String home1 = "";
//				if (testSiemens) {
//					// immagine SYMPHONY
//					home1 = findTestImages();
//					path1 = home1 + "/HR2A_testP4";
//					xStartRefline = X_START_REFLINE_TESTSIEMENS;
//					yStartRefline = Y_START_REFLINE_TESTSIEMENS;
//					xEndRefline = X_END_REFLINE_TESTSIEMENS;
//					yEndRefline = Y_END_REFLINE_TESTSIEMENS;
//				}
//				if (testGe) {
//					// immagine GE
//					home1 = findTestImages();
//					path1 = home1 + "/HR2A2_testP4";
//					xStartRefline = X_START_REFLINE_TESTGE;
//					yStartRefline = Y_START_REFLINE_TESTGE;
//					xEndRefline = X_END_REFLINE_TESTGE;
//					yEndRefline = Y_END_REFLINE_TESTGE;
//				}
//
//			}
//			if (!selftest) {
//				path1 = utils.imageSelection("SELEZIONARE IMMAGINE...");
//				if (path1 == null)
//					return;
//			}
//		}
//
//		if (autoCalled) {
//			retry = false;
//			do {
//				userSelection1 = utils.userSelectionAuto(VERSION, TYPE);
//				switch (userSelection1) {
//				case ABORT:
//					ab.close();
//					return;
//				case 2:
//					new AboutBox().about("Controllo MTF", this.getClass());
//					retry = true;
//					break;
//				case 3:
//					bstep = true;
//					retry = false;
//					break;
//				case 4:
//					bstep = false;
//					retry = false;
//					break;
//				}
//			} while (retry);
//			ab.close();
//
//			if (nTokens != TOKENS1) {
//				bm.ModelessMsg(VERSION + " >> ERRORE PARAMETRI CHIAMATA",
//						"CHIUDI");
//				return;
//			}
//
//			riga1 = Integer.parseInt(st.nextToken());
//			// Carico in memoria la tabella con la lista immagini generata
//			// da Sequenze
//			strRiga3 = new TableSequence().loadTable(fileDir + fileName);
//			// lr.dumpaTabella(strRiga3);
//			path1 = TableSequence.getPath(strRiga3, riga1);
//		}
//
//		ImagePlus imp1 = new ImagePlus();
//		String[][] info1 = rsi.getStandardInfo(strRiga3, riga1, tabl,
//				VERSION, autoCalled);
//		int misure1 = utils.setMeasure(MEAN + STD_DEV);
//
//		do {
//
//			IJ
//					.showStatus("Ricordiamo il comando RestoreSelection CTRL+SHIFT+E");
//
//			utils.closeResultsWindow();
//
//			// imp1 = utils.openImageNormal(path1);
//			imp1 = utils.openImageMaximized(path1);
//
//			double dimPixel = rd.readDouble(rd.readSubstring(rd
//					.readDicomParameter(imp1, DICOM_PIXEL_SPACING), 1));
//			Singleton.getSingleton().setGlobDou1(dimPixel);
//			// int Rows = utils
//			// .readInt(utils.readDicomParameter(imp1, DICOM_ROWS));
//			//
//			// if (Rows == MATRIX512)
//			// IJ.run("In");
//			// if (Rows == MATRIX256) {
//			// IJ.run("In");
//			// IJ.run("In");
//			// }
//			//
//			// Ora posiziono un segmento a 45° e poi
//			// chiedo all' umano o quadrumano di posizionarlo
//			// in coincidenza del lato sx del pacchetto 2 mm
//			// e poi premere STEP o RUN
//			//
//			ImageCanvas canvas = imp1.getWindow().getCanvas();
//			double mag = canvas.getMagnification();
//			double wid = imp1.getWidth();
//
//			if (!selftest) {
//
//				xStartRefline = rd.readDouble(Prefs.get("prefer.p4rmnAx",
//						"" + 0.4));
//				yStartRefline = rd.readDouble(Prefs.get("prefer.p4rmnAy",
//						"" + 0.4));
//				xEndRefline = rd.readDouble(Prefs.get("prefer.p4rmnBx",
//						"" + 0.8));
//				yEndRefline = rd.readDouble(Prefs.get("prefer.p4rmnBy",
//						"" + 0.8));
//
//				if (((Math.abs(xStartRefline - xEndRefline) < 0.1) && (Math
//						.abs(yStartRefline - yEndRefline) < 0.1))
//						|| ((Math.abs(xStartRefline - xEndRefline) > 1.0) && (Math
//								.abs(yStartRefline - yEndRefline) > 1.0))) {
//					xStartRefline = 0.4;
//					yStartRefline = 0.4;
//					xEndRefline = 0.8;
//					yEndRefline = 0.8;
//				}
//
//				xStartRefline = xStartRefline * wid / mag;
//				yStartRefline = yStartRefline * wid / mag;
//				xEndRefline = xEndRefline * wid / mag;
//				yEndRefline = yEndRefline * wid / mag;
//
//			}
//
//			int xStartReflineScreen = canvas.screenXD(xStartRefline);
//			int yStartReflineScreen = canvas.screenXD(yStartRefline);
//			int xEndReflineScreen = canvas.screenXD(xEndRefline);
//			int yEndReflineScreen = canvas.screenXD(yEndRefline);
//
//			imp1.setRoi(new Line(xStartReflineScreen, yStartReflineScreen,
//					xEndReflineScreen, yEndReflineScreen));
//			imp1.updateAndDraw();
//
//			if (!selftest) {
//				userSelection1 = bm
//						.ModelessMsg(
//								"Far coincidere il segmento  con il lato esterno sx delle linee 2 mm e premere CONTINUA",
//								"CONTINUA", "CHIUDI");
//				if (userSelection1 == ABORT)
//					return;
//
//			} else {
//				userSelection1 = bm.ModelessMsg(VERSION
//						+ " >> SELFTEST <07> ", "CONTINUA");
//			}
//
//			//
//			// Leggo la posizione finale del segmento
//			//
//			Roi roi = imp1.getRoi();
//			Line l1 = (Line) roi;
//			// coordinate dopo posizionamento segmento
//			xStartReflineUser = l1.x1;
//			yStartReflineUser = l1.y1;
//			xEndReflineUser = l1.x2;
//			yEndReflineUser = l1.y2;
//
//			Prefs.set("prefer.p4rmnAx", "" + xStartReflineUser / wid);
//			Prefs.set("prefer.p4rmnAy", "" + yStartReflineUser / wid);
//			Prefs.set("prefer.p4rmnBx", "" + xEndReflineUser / wid);
//			Prefs.set("prefer.p4rmnBy", "" + yEndReflineUser / wid);
//
//			Singleton.getSingleton().setGlobDou2(xStartReflineUser);
//			Singleton.getSingleton().setGlobDou3(yStartReflineUser);
//			Singleton.getSingleton().setGlobDou4(xEndReflineUser);
//			Singleton.getSingleton().setGlobDou5(yEndReflineUser);
//
//			if (bstep)
//				userSelection1 = bm.ModelessMsg("a2x= " + xStartReflineUser
//						+ "   a2y = " + yStartReflineUser + "   b2x = "
//						+ xEndReflineUser + "  b2y = " + yEndReflineUser,
//						"CONTINUA");
//
//			// -----------------------------------------------------------------------/
//			// determino ora la posizione rotrotraslata delle
//			// diverse roi per il 1024 risoluzione 2 mmlp
//			// -----------------------------------------------------------------------/
//			// Il punto in altro a sx del nostro segmento diventa il punto di
//			// origine
//			//
//			int xRoi = 0;
//			int yRoi = 0;
//			// -------------- 2 mm
//			xRoi = (int) (X_ROI_POSITION[0] / dimPixel);
//			yRoi = (int) (Y_ROI_POSITION[0] / dimPixel);
//			double[] dsd1 = utils.coord2D(xStartReflineUser, yStartReflineUser,
//					xEndReflineUser, yEndReflineUser, xRoi, yRoi, false);
//			// -------------- 1.5 mm
//			xRoi = (int) (X_ROI_POSITION[1] / dimPixel);
//			yRoi = (int) (Y_ROI_POSITION[1] / dimPixel);
//			double[] dsd2 = utils.coord2D(xStartReflineUser, yStartReflineUser,
//					xEndReflineUser, yEndReflineUser, xRoi, yRoi, false);
//			// -------------- 1 mm
//			xRoi = (int) (X_ROI_POSITION[2] / dimPixel);
//			yRoi = (int) (Y_ROI_POSITION[2] / dimPixel);
//			double[] dsd3 = utils.coord2D(xStartReflineUser, yStartReflineUser,
//					xEndReflineUser, yEndReflineUser, xRoi, yRoi, false);
//			// -------------- 0.5 mm
//			xRoi = (int) (X_ROI_POSITION[3] / dimPixel);
//			yRoi = (int) (Y_ROI_POSITION[3] / dimPixel);
//			double[] dsd4 = utils.coord2D(xStartReflineUser, yStartReflineUser,
//					xEndReflineUser, yEndReflineUser, xRoi, yRoi, false);
//			// -------------- 0.3 mm
//			xRoi = (int) (X_ROI_POSITION[4] / dimPixel);
//			yRoi = (int) (Y_ROI_POSITION[4] / dimPixel);
//			double[] dsd5 = utils.coord2D(xStartReflineUser, yStartReflineUser,
//					xEndReflineUser, yEndReflineUser, xRoi, yRoi, false);
//			// -------------- plexiglas
//			xRoi = (int) (X_ROI_POSITION[5] / dimPixel);
//			yRoi = (int) (Y_ROI_POSITION[5] / dimPixel);
//			double[] dsd6 = utils.coord2D(xStartReflineUser, yStartReflineUser,
//					xEndReflineUser, yEndReflineUser, xRoi, yRoi, false);
//			// -------------- acqua
//			xRoi = (int) (X_ROI_POSITION[6] / dimPixel);
//			yRoi = (int) (Y_ROI_POSITION[6] / dimPixel);
//			double[] dsd7 = utils.coord2D(xStartReflineUser, yStartReflineUser,
//					xEndReflineUser, yEndReflineUser, xRoi, yRoi, false);
//
//			// -------------------------------------------------------/
//
//			// offset rispetto al centro dell' immagine (nel nostro caso
//			// sarà rispetto al centro del fantoccio) espressi in pixel
//
//			int dRoi2mm = (int) (DIA_ROI[0] / dimPixel);
//			int xRoi2mm = (int) (dsd1[0] - dRoi2mm / 2);
//			int yRoi2mm = (int) (dsd1[1] - dRoi2mm / 2);
//
//			int dRoi1_5mm = (int) (DIA_ROI[1] / dimPixel);
//			int xRoi1_5mm = (int) (dsd2[0] - dRoi1_5mm / 2);
//			int yRoi1_5mm = (int) (dsd2[1] - dRoi1_5mm / 2);
//
//			int dRoi1mm = (int) (DIA_ROI[2] / dimPixel);
//			int xRoi1mm = (int) (dsd3[0] - dRoi1mm / 2);
//			int yRoi1mm = (int) (dsd3[1] - dRoi1mm / 2);
//
//			int dRoi_5mm = (int) (DIA_ROI[3] / dimPixel);
//			int xRoi_5mm = (int) (dsd4[0] - dRoi_5mm / 2);
//			int yRoi_5mm = (int) (dsd4[1] - dRoi_5mm / 2);
//
//			int dRoi_3mm = (int) (DIA_ROI[4] / dimPixel);
//			int xRoi_3mm = (int) (dsd5[0] - dRoi_3mm / 2);
//			int yRoi_3mm = (int) (dsd5[1] - dRoi_3mm / 2);
//
//			int dRoi_acqua = (int) (DIA_ROI[5] / dimPixel);
//			int xRoi_acqua = (int) (dsd6[0] - dRoi_acqua / 2);
//			int yRoi_acqua = (int) (dsd6[1] - dRoi_acqua / 2);
//
//			int dRoi_plexi = (int) (DIA_ROI[6] / dimPixel);
//			int xRoi_plexi = (int) (dsd7[0] - dRoi_plexi / 2);
//			int yRoi_plexi = (int) (dsd7[1] - dRoi_plexi / 2);
//
//			String macro = "makeOval(" + xRoi2mm + "," + yRoi2mm + ","
//					+ dRoi2mm + "," + dRoi2mm + ");" + "roiManager('Add');"
//					+ "makeOval(" + xRoi1_5mm + "," + yRoi1_5mm + ","
//					+ dRoi1_5mm + "," + dRoi1_5mm + ");" + "roiManager('Add');"
//					+ "makeOval(" + xRoi1mm + "," + yRoi1mm + "," + dRoi1mm
//					+ "," + dRoi1mm + ");" + "roiManager('Add');" + "makeOval("
//					+ xRoi_5mm + "," + yRoi_5mm + "," + dRoi_5mm + ","
//					+ dRoi_5mm + ");" + "roiManager('Add');" + "makeOval("
//					+ xRoi_3mm + "," + yRoi_3mm + "," + dRoi_3mm + ","
//					+ dRoi_3mm + ");" + "roiManager('Add');" + "makeOval("
//					+ xRoi_acqua + "," + yRoi_acqua + "," + dRoi_acqua + ","
//					+ dRoi_acqua + ");" + "roiManager('Add');" + "makeOval("
//					+ xRoi_plexi + "," + yRoi_plexi + "," + dRoi_plexi + ","
//					+ dRoi_plexi + ");" + "roiManager('Add');"
//					+ "roiManager('Combine');";
//			IJ.runMacro(macro, ""); // requires v1.33c or later
//			if (!selftest) {
//				userSelection1 = bm
//						.ModelessMsg(
//								"Posizionare con maggiore esattezza le roi e poi premere CONTINUA",
//								"CONTINUA");
//			} else {
//				userSelection1 = bm.ModelessMsg("Premere CONTINUA",
//						"CONTINUA");
//			}
//
//			IJ.runMacro("roiManager('Measure')", "");
//			IJ.runMacro("roiManager('Delete')", "");
//
//			cc4 = new CustomCanvasMTF(imp1);
//			new ImageWindow(imp1, cc4);
//			ImageCanvas ic1 = imp1.getWindow().getCanvas();
//
//			Singleton.getSingleton().setGlobBmag(ic1.getMagnification());
//
//			if (!selftest) {
//				userSelection1 = bm.ModelessMsg(
//						"Quale è l'ultimo gruppo visibile?", "- 5 -", "- 4 -",
//						"- 3 -", "- 2 -", "- 1 -");
//
//				visualResolution = userSelection1;
//			}
//
//			//
//			// Display dei risultati
//			//
//			ResultsTable rt1 = Analyzer.getResultsTable();
//			int nDati = rt1.getCounter();
//
//			String t1 = "TESTO          ";
//			int[][] tabValori = new int[nDati][2];
//			for (int i2 = 0; i2 < nDati; i2++) {
//
//				tabValori[i2][0] = (int) rt1.getValue("Mean", i2);
//				tabValori[i2][1] = (int) rt1.getValue("StdDev", i2);
//			}
//
//			double ds20 = tabValori[0][1];
//			double ds15 = tabValori[1][1];
//			double ds10 = tabValori[2][1];
//			double ds05 = tabValori[3][1];
//			double ds03 = tabValori[4][1];
//
//			double dsAcqua = tabValori[5][1];
//			double dsPlexi = tabValori[6][1];
//			double siAcqua = tabValori[5][0];
//			double siPlexi = tabValori[6][0];
//			double mod20 = (Math.PI * Math.sqrt(2.0) / 2.0)
//					* (Math
//							.sqrt(Math.pow(ds20, 2)
//									- (Math.pow(dsAcqua, 2) + Math.pow(dsPlexi,
//											2)) / 2) / Math.abs(siPlexi
//							- siAcqua));
//			double mod15 = (Math.PI * Math.sqrt(2.0) / 2.0)
//					* (Math
//							.sqrt(Math.pow(ds15, 2)
//									- (Math.pow(dsAcqua, 2) + Math.pow(dsPlexi,
//											2)) / 2) / Math.abs(siPlexi
//							- siAcqua));
//			double mod10 = (Math.PI * Math.sqrt(2.0) / 2.0)
//					* (Math
//							.sqrt(Math.pow(ds10, 2)
//									- (Math.pow(dsAcqua, 2) + Math.pow(dsPlexi,
//											2)) / 2) / Math.abs(siPlexi
//							- siAcqua));
//			double mod05 = (Math.PI * Math.sqrt(2.0) / 2.0)
//					* (Math
//							.sqrt(Math.pow(ds05, 2)
//									- (Math.pow(dsAcqua, 2) + Math.pow(dsPlexi,
//											2)) / 2) / Math.abs(siPlexi
//							- siAcqua));
//			double mod03 = (Math.PI * Math.sqrt(2.0) / 2.0)
//					* (Math
//							.sqrt(Math.pow(ds03, 2)
//									- (Math.pow(dsAcqua, 2) + Math.pow(dsPlexi,
//											2)) / 2) / Math.abs(siPlexi
//							- siAcqua));
//
//			ResultsTable rt = rsi.putStandardInfoRT(info1);
//
//			int col = 2;
//			rt.setHeading(++col, "roi_x");
//			rt.setHeading(++col, "roi_y");
//			rt.setHeading(++col, "roi_b");
//			rt.setHeading(++col, "roi_h");
//			rt.addLabel(t1, "segm_riferim");
//			rt.addValue(3, xStartRefline);
//			rt.addValue(4, yStartRefline);
//			rt.addValue(5, xEndRefline);
//			rt.addValue(6, yEndRefline);
//
//			rt.incrementCounter();
//
//			rt.addLabel(t1, "MTF_2.0_LPmm");
//			rt.addValue(2, mod20);
//			rt.addValue(3, xRoi2mm);
//			rt.addValue(4, yRoi2mm);
//			rt.addValue(5, dRoi2mm);
//			rt.addValue(6, dRoi2mm);
//
//			rt.incrementCounter();
//			rt.addLabel(t1, "MTF_1.5_LPmm");
//			rt.addValue(2, mod15);
//			rt.addValue(3, xRoi1_5mm);
//			rt.addValue(4, yRoi1_5mm);
//			rt.addValue(5, dRoi1_5mm);
//			rt.addValue(6, dRoi1_5mm);
//
//			rt.incrementCounter();
//			rt.addLabel(t1, "MTF_1.0_LPmm");
//			rt.addValue(2, mod10);
//			rt.addValue(3, xRoi1mm);
//			rt.addValue(4, yRoi1mm);
//			rt.addValue(5, dRoi1mm);
//			rt.addValue(6, dRoi1mm);
//
//			rt.incrementCounter();
//			rt.addLabel(t1, "MTF_0.5_LPmm");
//			rt.addValue(2, mod05);
//			rt.addValue(3, xRoi_5mm);
//			rt.addValue(4, yRoi_5mm);
//			rt.addValue(5, dRoi_5mm);
//			rt.addValue(6, dRoi_5mm);
//
//			rt.incrementCounter();
//			rt.addLabel(t1, "MTF_0.3_LPmm");
//			rt.addValue(2, mod03);
//			rt.addValue(3, xRoi_3mm);
//			rt.addValue(4, yRoi_3mm);
//			rt.addValue(5, dRoi_3mm);
//			rt.addValue(6, dRoi_3mm);
//
//			rt.incrementCounter();
//			rt.addLabel(t1, "DimPix");
//			rt.addValue(2, dimPixel);
//
//			rt.incrementCounter();
//			rt.addLabel(t1, "Visual");
//			rt.addValue(2, visualResolution);
//
//			rt.show("Results");
//
//			userSelection3 = 0;
//			if (autoCalled) {
//				userSelection3 = bm.ModelessMsg(
//						"Accettare il risultato delle misure?", "ACCETTA",
//						"RIFAI");
//				switch (userSelection3) {
//				case 1:
//					utils.cleanUp();
//					IJ.selectWindow("ROI Manager");
//					IJ.run("Close");
//					accetta = false;
//					break;
//				case 2:
//					accetta = true;
//					break;
//				}
//			} else {
//				if (selftest == true) {
//					if (testSiemens)
//						testSymphony(mod20, mod15, mod10, mod05, mod03);
//
//					if (testGe)
//						testGe(mod20, mod15, mod10, mod05, mod03);
//				} else {
//					userSelection3 = bm
//							.ModelessMsg(
//									"Fine programma, in modo STAND-ALONE salvare A MANO la finestra Risultati",
//									"CONTINUA");
//				}
//				accetta = true;
//			}
//		} while (!accetta);
//
//		// --------------------------------------------------------------------------------------/
//		// Salvataggio della ResultsTable in Excel
//		// --------------------------------------------------------------------------------------/
//
//		if (autoCalled) {
//			IJ.run("Excel...", "select...=[" + fileDir + fileXls + "]");
//			TableSequence lr = new TableSequence();
//			lr.putDone(strRiga3, riga1);
//			lr.writeTable(fileDir + fileName, strRiga3);
//		}
//
//		utils.resetResultsTable();
//		utils.resetMeasure(misure1);
//	//	io.deleteDirFromName(TEST_DIRECTORY);
//
//		if (autoCalled) {
//			utils.cleanUp();
//			IJ.selectWindow("ROI Manager");
//			IJ.run("Close");
//		}
//
//	} // run
//
//	/**
//	 * built-in test per la immagine SYMPHONY in modo PROVA
//	 * 
//	 * @param mod20
//	 *            mtf spaziatura 2.0
//	 * @param mod15
//	 *            mtf spaziatura 1.5
//	 * @param mod10
//	 *            mtf spaziatura 1.0
//	 * @param mod05
//	 *            mtf spaziatura 0.5
//	 * @param mod03
//	 *            mtf spaziatura 0.3
//	 */
//	private void testSymphony(double mod20, double mod15, double mod10,
//			double mod05, double mod03) {
//		boolean testok = true;
//		UtilAyv utils = new UtilAyv();
//		
//		ButtonMessages bm = new ButtonMessages();
//	
//		
//		double rightValue = 0.9598900737778213; // formula col -
//		if (mod20 != rightValue) {
//			IJ.log("MTF2.0 ERRATA " + mod20 + " anzichè " + rightValue);
//			testok = false;
//		}
//		rightValue = 0.9866962668164938; // formula col -
//		if (mod15 != rightValue) {
//			IJ.log("MTF1.5 ERRATA " + mod15 + " anzichè " + rightValue);
//			testok = false;
//		}
//		rightValue = 0.9883716512349785; // formula col -
//		if (mod10 != rightValue) {
//			IJ.log("MTF1.0 ERRATA " + mod10 + " anzichè " + rightValue);
//			testok = false;
//		}
//		rightValue = 0.14203500307561293; // formula col -
//		if (mod05 != rightValue) {
//			IJ.log("MTF0.5 ERRATA " + mod05 + " anzichè " + rightValue);
//			testok = false;
//		}
//		rightValue = 0.3751277435706519; // formula col -
//		if (mod03 != rightValue) {
//			IJ.log("MTF0.3 ERRATA " + mod03 + " anzichè " + rightValue);
//			testok = false;
//		}
//		if (testok == true)
//			bm.ModelessMsg("Fine SelfTest TUTTO OK  <42>", "CONTINUA");
//		else
//			bm.ModelessMsg("Fine SelfTest CON ERRORI  <43>", "CONTINUA");
//		utils.cleanUp();
//		IJ.selectWindow("ROI Manager");
//		IJ.run("Close");
//
//	} // testSymphony
//
//	/**
//	 * built-in test per la immagine GE in modo PROVA
//	 * 
//	 * @param mod20
//	 *            mtf spaziatura 2.0
//	 * @param mod15
//	 *            mtf spaziatura 1.5
//	 * @param mod10
//	 *            mtf spaziatura 1.0
//	 * @param mod05
//	 *            mtf spaziatura 0.5
//	 * @param mod03
//	 *            mtf spaziatura 0.3
//	 */
//	private void testGe(double mod20, double mod15, double mod10, double mod05,
//			double mod03) {
//		boolean testok = true;
//		UtilAyv utils = new UtilAyv();
//		ButtonMessages bm = new ButtonMessages();
//
//		double rightValue = 0.9648520059889102; // formula col -
//		if (mod20 != rightValue) {
//			IJ.log("MTF2.0 ERRATA " + mod20 + " anzichè " + rightValue);
//			testok = false;
//		}
//		rightValue = 1.0191761573797278; // formula col -
//		if (mod15 != rightValue) {
//			IJ.log("MTF1.5 ERRATA " + mod15 + " anzichè " + rightValue);
//			testok = false;
//		}
//		rightValue = 0.8318990365553754; // formula col -
//		if (mod10 != rightValue) {
//			IJ.log("MTF1.0 ERRATA " + mod10 + " anzichè " + rightValue);
//			testok = false;
//		}
//		rightValue = 0.14254549598153793; // formula col -
//		if (mod05 != rightValue) {
//			IJ.log("MTF0.5 ERRATA " + mod05 + " anzichè " + rightValue);
//			testok = false;
//		}
//		rightValue = 0.2685309238398184; // formula col -
//		if (mod03 != rightValue) {
//			IJ.log("MTF0.3 ERRATA  > " + mod03 + " anzichè " + rightValue);
//			testok = false;
//		}
//		if (testok == true)
//			bm.ModelessMsg("Fine SelfTest TUTTO OK  <42>", "CONTINUA");
//		else
//			bm.ModelessMsg("Fine SelfTest CON ERRORI  <43>", "CONTINUA");
//		utils.cleanUp();
//		IJ.selectWindow("ROI Manager");
//		IJ.run("Close");
//
//	} // testGe
//
//	/**
//	 * genera una directory temporanea e vi estrae le immagini di test da
//	 * test2.jar
//	 * 
//	 * @return home1 path della directory temporanea con le immagini di test
//	 */
//	private String findTestImages() {
//
//		InputOutput io = new InputOutput();
//
//		io.extractFromJAR(TEST_FILE, "HR2A_testP4", "./Test2/");
//		io.extractFromJAR(TEST_FILE, "HR2A2_testP4", "./Test2/");
//		String home1 = this.getClass().getResource(TEST_DIRECTORY).getPath();
//		return (home1);
//	} // findTestImages

//} // p4rmn
