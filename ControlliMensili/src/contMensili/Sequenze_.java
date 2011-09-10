package contMensili;

import java.io.*;
import java.net.URL;
import java.util.*;

import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.MyConst;
import utils.MyFileLogger;
import utils.MyLog;
import utils.TableCode;
import utils.TableExpand;
import utils.ReadDicom;
import utils.DirectoryChooser2;
import utils.InputOutput;
import utils.TableSequence;
import utils.TableUtils;
import utils.TableVerify;
import utils.UtilAyv;

import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.*;
import ij.Prefs;

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
 * 
 * Main plugin for the Quality Controls of MRI images.
 * 
 * The program, for a new search, make a recursive search in the selected
 * directory and sub-directories, write the list of processing files in ayv.txt.
 * After that it uses the list in ayv.txt to call the right plugin for
 * processing the different types of image.
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */
public class Sequenze_ implements PlugIn {
	private final static int ABORT = 1;

	public static String VERSION = "Sequenze_v4.10_10dec08_";

	public int location;

	public void run(String arg) {

		// inizializziamo l'eventuale logger file MyFileLog.txt
		// ricordimaci che vengono loggati tutti i messaggi
		// File fl = new File("MyFileLog.txt");
		// fl.delete();

		// MyFileLogger.logger.severe("Sequenze"+ MyFileLogger.here("aaaa"));
		// MyFileLogger.logger.warning("Sequenze"+ MyFileLogger.here("aaaa"));
		MyFileLogger.logger.info("-----INIZIO Sequenze----");

		if (this.getClass().getResource("/iw2ayv.jar") == null) {
			IJ.error("ATTENZIONE, manca il file iw2ayv.jar");
			return;
		}
		if (this.getClass().getResource("/Excel_Writer.jar") == null) {
			IJ.error("ATTENZIONE, manca il file Excel_Writer.jar");
			return;
		}

		String startingDir = Prefs.get(MyConst.PREFERENCES_1,
				MyConst.DEFAULT_PATH);
		MyFileLogger.logger.info("Sequenze_>>> startingDir letta= "
				+ startingDir);

		boolean startingDirExist = new File(startingDir).exists();

		// IJ.log("il sistema dice che siamo in:"
		// + System.getProperty("user.home"));
		//
		// IJ.log("il file trovami si trova in:"
		// + new InputOutput().findResource("Sequenze_.class"));

		String[][] tableCode = TableCode.loadTable(MyConst.CODE_FILE);
		String[][] tableExpand = TableExpand.loadTable(MyConst.EXPAND_FILE);
		new AboutBox().about("Scansione automatica cartelle", this.getClass());
		IJ.wait(2000);
		new AboutBox().close();

		GenericDialog gd = new GenericDialog("", IJ.getInstance());
		gd.addCheckbox("Nuovo controllo", false);
		gd.addCheckbox("SelfTest", false);

		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		}
		boolean nuovo1 = gd.getNextBoolean();
		boolean self1 = gd.getNextBoolean();

		if (self1) {
			if (!new InputOutput().checkJar(MyConst.TEST_FILE)) {
				UtilAyv.noTest2();
				return;
			}

			// IJ.runPlugIn("contMensili.p2rmn_", "-1");
			IJ.runPlugIn("contMensili.p3rmn_", "-1");
			IJ.runPlugIn("contMensili.p4rmn_", "-1");
			IJ.runPlugIn("contMensili.p5rmn_", "-1");
			IJ.runPlugIn("contMensili.p6rmn_", "-1");
			IJ.runPlugIn("contMensili.p7rmn_", "-1");
			IJ.runPlugIn("contMensili.p8rmn_", "-1");
			// IJ.runPlugIn("contMensili.p9rmn_", "-1");
			ButtonMessages.ModelessMsg(
					"Sequenze: fine selfTest, vedere Log per risultati",
					"CONTINUA");
			return;
		} else if (nuovo1 || !startingDirExist) {
			DirectoryChooser2 od2 = new DirectoryChooser2(
					"SELEZIONARE MA NON APRIRE LA CARTELLA IMMAGINI",
					startingDir);

			startingDir = od2.getDirectory();
			if (startingDir == null)
				return;
			Prefs.set("prefer.string1", startingDir);
			// MyFileLogger.logger.info("Sequenze_>>> startingDir salvata= "
			// + startingDir);

			String aux1 = Prefs
					.get(MyConst.PREFERENCES_1, MyConst.DEFAULT_PATH);
			// MyFileLogger.logger.info("Sequenze_>>> startingDir riletta= "
			// + aux1);

		}

		boolean fileExist = new File(startingDir + MyConst.SEQUENZE_FILE)
				.exists();

		if ((nuovo1) && (fileExist)) {
			new ButtonMessages();
			int userSelection1 = ButtonMessages
					.ModelessMsg(
							"Attenzione, nella cartella selezionata i files iw2ayv.txt e Results1.xls"
									+ "\n"
									+ "esistono già  premere SOVRASCRIVI per cancellarli, CONTINUA per utilizzarli, altrimenti CHIUDI",
							"SOVRASCRIVI", "CONTINUA", "CHIUDI");
			switch (userSelection1) {
			case ABORT:
				return;
			case 2:
				nuovo1 = false;
				break;
			case 3:
				nuovo1 = true;
				break;
			}
		}

		if (!fileExist) {
			nuovo1 = true;
		}

		if (nuovo1) {
			//
			// se è stato selezionato un nuovo set di misure cancello sia il
			// file directory che il file excel dei risultati
			//
			File fx = new File(startingDir + MyConst.SEQUENZE_FILE);
			if (fx.exists())
				fx.delete();
			File fy = new File(startingDir + MyConst.XLS_FILE);
			if (fy.exists())
				fy.delete();
			File fz = new File(startingDir + MyConst.TXT_FILE);
			if (fz.exists())
				fz.delete();
			// MyLog.here();
			// IJ.log("startingDir=" + startingDir);

			List<File> result = getFileListing(new File(startingDir));
			if (result == null) {
				MyLog.here("getFileListing.result==null");
			}
			String[] list = new String[result.size()];
			int j1 = 0;
			for (File file : result) {
				list[j1++] = file.getPath();
			}
			String[][] tableSequenceLoaded = generateSequenceTable(list,
					tableCode, tableExpand);

			// cancello gli eventuali messaggi di ImageJ dal log
			// if (WindowManager.getFrame("Log") != null) {
			// IJ.selectWindow("Log");
			// IJ.run("Close");
			// }
			if (tableSequenceLoaded == null) {
				MyLog.here("non sono state trovate immagini da analizzare");
				return;
			}
			String[][] tableSequenceSorted = bubbleSortSequenceTable(tableSequenceLoaded);

			String[][] tableSequenceReordered = reorderSequenceTable(
					tableSequenceSorted, tableCode);

			String[][] listProblems = verifySequenceTable(
					tableSequenceReordered, tableCode);

			logVerifySequenceTable(listProblems);

			boolean success = new TableSequence().writeTable(startingDir
					+ MyConst.SEQUENZE_FILE, tableSequenceReordered);
			if (!success)
				IJ.log("Problemi creazione file iw2ayv.txt");
		}
		// MyLog.here();
		// IJ.log("startingDir=" + startingDir);

		String[][] tableSequenceReloaded = new TableSequence()
				.loadTable(startingDir + MyConst.SEQUENZE_FILE);
		callPluginsFromSequenceTable(tableSequenceReloaded, tableCode, false);
	}

	/**
	 * Legge ricorsivamente la directory e relative sottodirectory
	 * 
	 * @author www.javapractices.com
	 * @author Alex Wong
	 * @author anonymous user
	 * 
	 * @param startingDir
	 *            directory "radice"
	 * @return lista dei path dei file
	 */
	public List<File> getFileListing(File startingDir) {
		List<File> result = new ArrayList<File>();
		File[] filesAndDirs = startingDir.listFiles();
		if (filesAndDirs == null)
			return null;
		List<File> filesDirs = Arrays.asList(filesAndDirs);
		for (File file : filesDirs) {
			if (!file.isFile()) {
				// must be a directory
				// recursive call !!
				List<File> deeperList = getFileListing(file);
				result.addAll(deeperList);
			} else {
				result.add(file);
			}
		}
		return result;
	}

	/**
	 * Analizza tutti i file e, se sono compatibili con i codici di sequenza
	 * elencati in codici.txt, compila i vari campi della tabella dei file da
	 * analizzare
	 * 
	 * @param pathList
	 *            lista dei file presenti nelle directory e relative
	 *            sottodirectory selezionate.
	 * @param tableCode2
	 *            contenuto di codici.txt
	 * @param tableExpand4
	 *            contenuto di expand.txt
	 * @return restituisce una TableSequence
	 */

	public String[][] generateSequenceTable(String[] pathList,
			String[][] tableCode2, String[][] tableExpand4) {

		List<String> vetConta = new ArrayList<String>();
		List<String> vetPath = new ArrayList<String>();
		List<String> vetCodice = new ArrayList<String>();
		List<String> vetCoil = new ArrayList<String>();
		List<String> vetImaDaPassare = new ArrayList<String>();
		List<String> vetSerie = new ArrayList<String>();
		List<String> vetAcq = new ArrayList<String>();
		List<String> vetIma = new ArrayList<String>();
		List<String> vetAcqTime = new ArrayList<String>();
		List<String> vetEchoTime = new ArrayList<String>();
		List<String> vetFatto = new ArrayList<String>();

		if (pathList == null) {
			IJ.log("loadList2.pathList = null");
			return null;
		}
		if (tableCode2 == null) {
			IJ.log("loadList2.tableCode2 = null");
			return null;
		}
		int count3 = 0;
		for (int i1 = 0; i1 < pathList.length; i1++) {
			IJ.showStatus(i1 + " / " + pathList.length);
			IJ.redirectErrorMessages();

			int type = (new Opener()).getFileType(pathList[i1]);

			if (type == Opener.DICOM) {

				ImagePlus imp1 = new Opener().openImage(pathList[i1]);
				if (imp1 == null) {
					// IJ.log("" + i1 + " file " + pathList[i1] +
					// " problematic");
					continue;
				}

				if (!ReadDicom.hasHeader(imp1)) {
					// IJ.log("" + i1 + " file " + pathList[i1] + " not dicom");
					continue;
				}

				String path1 = pathList[i1];
				String fileName = path1.substring(path1.lastIndexOf("/") + 1,
						path1.length());
				String codice = "-----";
				String subCodice = "-----";
				if (fileName.length() >= 5) {
					subCodice = fileName.substring(0, 5).trim();
				}
				new InputOutput();
				if (InputOutput.isCode(subCodice, tableCode2)) {
					codice = subCodice;
				} else {
					String seriesDescription = ReadDicom.readDicomParameter(
							imp1, MyConst.DICOM_SERIES_DESCRIPTION);
					if (seriesDescription.length() >= 5) {
						codice = seriesDescription.substring(0, 5).trim();
					}
				}
				String coil = UtilAyv.firstCoil(ReadDicom.readDicomParameter(
						imp1, MyConst.DICOM_COIL));

				if (coil.equals("MISSING")) {
					coil = new UtilAyv().kludge(pathList[i1]);
				}

				String numSerie = ReadDicom.readDicomParameter(imp1,
						MyConst.DICOM_SERIES_NUMBER);
				String numAcq = ReadDicom.readDicomParameter(imp1,
						MyConst.DICOM_ACQUISITION_NUMBER);
				String numIma = ReadDicom.readDicomParameter(imp1,
						MyConst.DICOM_IMAGE_NUMBER);
				String acqTime = deleteDot(ReadDicom.readDicomParameter(imp1,
						MyConst.DICOM_ACQUISITION_TIME));
				String echoTime = ReadDicom.readDicomParameter(imp1,
						MyConst.DICOM_ECHO_TIME);
				if (echoTime.compareTo("") == 0)
					echoTime = "0";
				String fatto = "0";
				boolean trovato = false;
				int tableRow = 0;
				for (int j1 = 0; j1 < tableCode2.length; j1++) {
					if (codice.equals(tableCode2[j1][0])) {
						tableRow = j1;
						trovato = true;
						break;
					}
				}
				if (trovato) {
					// count3++;
					// se il codice è conosciuto aggiunge i dati
					vetConta.add("" + (count3));
					count3++;
					vetPath.add(path1);
					vetCodice.add(codice);
					vetCoil.add(coil);
					vetImaDaPassare.add(tableCode2[tableRow][1]);
					vetSerie.add(numSerie);
					vetAcq.add(numAcq);
					vetIma.add(numIma);
					vetAcqTime.add(acqTime);
					vetEchoTime.add(echoTime);
					vetFatto.add(fatto);
					String[] espansione;
					// vedo se occorre espandere
					espansione = expandCode(codice, echoTime, tableExpand4);
					if (espansione != null) {
						// count3++;
						vetConta.add("" + (count3));
						count3++;
						vetPath.add(path1);
						// IJ.log("espando con "+espansione[2]+" "+espansione[3]);
						vetCodice.add(espansione[2]);
						vetCoil.add(coil);
						vetImaDaPassare.add(espansione[3]);
						vetSerie.add(numSerie);
						vetAcq.add(numAcq);
						vetIma.add(numIma);
						vetAcqTime.add(acqTime);
						vetEchoTime.add(echoTime);
						vetFatto.add(fatto);
					}
				} else {
					// non aggiunge alcun dato
				}
			}
		}
		// a questo punto non mi resta che creare la tabella e riversarvi i dati
		// dagli ArrayList
		String[][] tableVuota = TableSequence.createEmptyTable(count3,
				TableSequence.COLUMNS);

		String[][] tablePass1 = TableSequence.writeColumn(tableVuota,
				ArrayUtils.arrayListToArrayString(vetConta), TableSequence.ROW);
		String[][] tablePass2 = TableSequence.writeColumn(tablePass1,
				ArrayUtils.arrayListToArrayString(vetPath), TableSequence.PATH);
		String[][] tablePass3 = TableSequence.writeColumn(tablePass2,
				ArrayUtils.arrayListToArrayString(vetCodice),
				TableSequence.CODE);
		String[][] tablePass4 = TableSequence.writeColumn(tablePass3,
				ArrayUtils.arrayListToArrayString(vetCoil), TableSequence.COIL);
		String[][] tablePass5 = TableSequence.writeColumn(tablePass4,
				ArrayUtils.arrayListToArrayString(vetImaDaPassare),
				TableSequence.IMA_PASS);
		String[][] tablePass6 = TableSequence.writeColumn(tablePass5,
				ArrayUtils.arrayListToArrayString(vetSerie),
				TableSequence.SERIE);
		String[][] tablePass7 = TableSequence.writeColumn(tablePass6,
				ArrayUtils.arrayListToArrayString(vetAcq), TableSequence.ACQ);
		String[][] tablePass8 = TableSequence.writeColumn(tablePass7,
				ArrayUtils.arrayListToArrayString(vetIma), TableSequence.IMA);
		String[][] tablePass9 = TableSequence.writeColumn(tablePass8,
				ArrayUtils.arrayListToArrayString(vetAcqTime),
				TableSequence.TIME);
		String[][] tablePass10 = TableSequence.writeColumn(tablePass9,
				ArrayUtils.arrayListToArrayString(vetEchoTime),
				TableSequence.ECHO);
		String[][] tablePass11 = TableSequence
				.writeColumn(tablePass10,
						ArrayUtils.arrayListToArrayString(vetFatto),
						TableSequence.DONE);
		return tablePass11;
	}

	/**
	 * Espande i codici delle immagini, utilizzando il file expand.txt
	 * 
	 * @param codice
	 *            codice della misura da espandere
	 * @param eco
	 *            tempo di eco della misura da espandere
	 * @param tableExpand4
	 *            tabella contenente i dati di expand.txt
	 * @return vettore contenente i dati espansi
	 */
	public String[] expandCode(String codice, String eco,
			String[][] tableExpand4) {
		if (codice == null)
			return null;
		if (eco == null)
			return null;
		if (tableExpand4 == null)
			return null;
		String[] out = new String[4];
		for (int i2 = 0; i2 < tableExpand4.length; i2++) {
			String codiceExpand = TableExpand.getOldCode(tableExpand4, i2);
			String ecoExpand = TableExpand.getEcho(tableExpand4, i2);
			if ((codice.equals(codiceExpand)) && (eco.equals(ecoExpand))) {
				out[0] = tableExpand4[i2][TableExpand.OLD_CODE];
				out[1] = tableExpand4[i2][TableExpand.ECHO];
				out[2] = tableExpand4[i2][TableExpand.NEW_CODE];
				out[3] = tableExpand4[i2][TableExpand.IMA_PASS];
				return out;
			}
		}
		return null;
	}

	/**
	 * Delete the dot in a string
	 * 
	 * @param strIn
	 *            stringa contenente un numero separato dal punto
	 * @return stringa contenente il numero senza la separazione del punto
	 */
	public String deleteDot(String strIn) {
		String strOut = "";
		int dot = strIn.indexOf('.');
		if (dot > 0) {
			String beforedot = strIn.substring(0, dot);
			String afterdot = strIn.substring(dot + 1, strIn.length());
			strOut = beforedot.concat(afterdot).trim();
		} else {
			strOut = strIn;
		}
		return strOut;
	}

	/**
	 * Scrive una riga nella tabella dei dati
	 * 
	 * @param inTable
	 *            tabella su cui scrivere
	 * @param vetData
	 *            dati da scrivere in tabella
	 * @param column
	 *            numero della riga in cui scrivere
	 * @return duplicato tabella con scritta la riga
	 */
	public String[][] writeTableColumn(String[][] inTable,
			List<String> vetData, int column) {
		String[][] outTable = TableSequence.writeColumn(inTable,
				ArrayUtils.arrayListToArrayString(vetData), column);
		return outTable;
	}

	/**
	 * E' il motore che si preoccupa di chiamare i vari plugin, passando sulla
	 * linea di comando il numero della linea di iw2ayv.txt da analizzare. Tiene
	 * conto dei controlli già effettuati, per cui si può spegnere il PC e
	 * ripartire ad analizzare dal file successivo all'ultimo analizzato con
	 * successo.
	 * 
	 * @param tableSequenze5
	 *            è il file iw2ayv.txt ricaricato da disco
	 * @param tableCode5
	 *            è il file codici2.txt caricato da disco
	 */
	public String[][] callPluginsFromSequenceTable(String[][] tableSequenze5,
			String[][] tableCode5, boolean test) {

		// Attenzione: contrariamente a quanto scritto più sotto, per
		// la struttura della tableSequenze è stata creata la classe
		// TableSequence che si occupa di tutto e in cui vengono definiti:
		// ROW = 0, PATH = 1, CODE = 2, COIL = 3, IMA_PASS = 4, SERIE = 5,
		// ACQ = 6, IMA = 7, TIME = 8, ECHO = 9, DONE = 10, COLUMNS = 11
		// facendo riferimento a questi valori si ha il vantaggio che sono
		// definiti in un unico punto. Per la struttura della TableCode è stata
		// creata la classe TableCode che si occupa di tutto e in cui vengono
		// definiti:
		// CODE = 0, IMA_PASS = 1, IMA_TOTAL = 2, COIL = 3, PLUGIN = 4.
		// facendo riferimento a questi valori si ha il vantaggio che vengono
		// definiti in un unico punto.
		//

		// ##########--OBSOLETO-OBSOLETO--OBSOLETO--################
		// OBSOLETO La struttura della tabella tableSequenze è la seguente:
		// OBSOLETO tableSequenze[n][0] = n, contatore progressivo
		// OBSOLETO tableSequenze[n][1] = path file
		// OBSOLETO tableSequenze[n][2] = codice
		// OBSOLETO tableSequenze[n][3] = bobina
		// OBSOLETO tableSequenze[n][4] = immagini da passare ( tabl[w1][1] )
		// OBSOLETO tableSequenze[n][5] = numSerie
		// OBSOLETO tableSequenze[n][6] = numAcq
		// OBSOLETO tableSequenze[n][7] = numIma
		// OBSOLETO tableSequenze[n][8] = acqTime
		// OBSOLETO tableSequenze[n][9] = echoTime
		// OBSOLETO tableSequenze[n][10] = fatto, default 0
		// OBSOLETO La struttura di TableCode è invece quella del file
		// OBSOLETO codici.txt:
		// OBSOLETO tableCode [n][0] = codice
		// OBSOLETO tableCode [n][1] = numero immagini da passare al plugin
		// OBSOLETO tableCode [n][2] = numero immagini totali
		// OBSOLETO tableCode [n][3] = nome del plugin da utilizzare per quel
		// codice
		// ##########--OBSOLETO-OBSOLETO--OBSOLETO--################

		if (tableSequenze5 == null) {
			IJ.log("callPluginsFromSequenceTable riceve NULL : Nessuna immagine da analizzare");
			return null;
		}

		int j1 = 0;
		List<String> vetPlugin = new ArrayList<String>();
		List<String> vetArgomento = new ArrayList<String>();
		while (j1 < tableSequenze5.length) {
			if (TableSequence.getDone(tableSequenze5, j1).equals("0")) {
				String plugin = pluginToBeCalledWithCoil(j1, tableSequenze5,
						tableCode5);
				String argomento = argumentForPluginToBeCalled(j1,
						tableSequenze5);
				if ((plugin == null) || (argomento == null)) {
					j1++;
				} else {
					new TableSequence();
					int numImaDaPassare = Integer.parseInt(TableSequence
							.getImaPass(tableSequenze5, j1));
					if (numImaDaPassare == 0) {
						j1++;
					} else {
						MyFileLogger.logger
								.info("Sequenze.callPluginFromSequenceTable >>> plugin= "
										+ plugin + " argomento= " + argomento);

						pluginRunner(plugin, argomento, test);
						vetPlugin.add(plugin);
						vetArgomento.add(argomento);
						j1 = j1 + numImaDaPassare;
					}
				}
			} else {
				j1++;
			}
		}
		String[][] chiamate = new String[vetPlugin.size()][2];
		for (int i1 = 0; i1 < vetPlugin.size(); i1++) {
			chiamate[i1][0] = vetPlugin.get(i1);
			chiamate[i1][1] = vetArgomento.get(i1);
		}

		// MyLog.here();
		// MyLog.logMatrix(chiamate, "chiamate");

		return chiamate;

	}

	/***
	 * Effettua il run di un plugin (abbiamo così il plugin sequenze che è in
	 * grado di chiamare i plugins per le eleborazioni delle varie immagini.
	 * 
	 * @param plugin
	 * @param argomento
	 * @param test
	 */
	public void pluginRunner(String plugin, String argomento, boolean test) {
		if (!test)
			IJ.runPlugIn(plugin, argomento);
	}

	/**
	 * Cerca in tabella il nome del plugin da lanciare per il codice
	 * corrispondente alla linea di ayv.txt elaborata al momento. Potremo avere
	 * ad esempio "p6rmn_"
	 * 
	 * @param lineNumber
	 *            numero linea tableSequenze in elaborazione (la prima con
	 *            "fatto" =0
	 * @param tableSequenze
	 *            tabella sequenze in memoria
	 * @param tableCode
	 *            tabella codici in memoria
	 * @return nome del plugin da chiamare
	 */
	public String pluginToBeCalled(int lineNumber, String[][] tableSequenze,
			String[][] tableCode) {
		if (tableSequenze == null)
			return null;
		if (tableCode == null)
			return null;
		String nome = null;
		if (TableSequence.getDone(tableSequenze, lineNumber).equals("0")) {
			for (int j2 = 0; j2 < tableCode.length; j2++) {
				if (TableSequence.getCode(tableSequenze, lineNumber).equals(
						TableCode.getCode(tableCode, j2))) {
					nome = "contMensili."
							+ TableCode.getPluginName(tableCode, j2);
					break;
				}
			}
		}
		return nome;
	}

	/**
	 * Cerca in tabella il nome del plugin da lanciare per il codice
	 * corrispondente alla linea di ayv.txt elaborata al momento. Potremo avere
	 * ad esempio "p6rmn_". Utilizza anche la colonna "COIL"
	 * 
	 * @param lineNumber
	 *            numero linea tableSequenze in elaborazione (la prima con
	 *            "fatto" =0
	 * @param tableSequenze
	 *            tabella sequenze in memoria
	 * @param tableCode
	 *            tabella codici in memoria
	 * @return nome del plugin da chiamare
	 */
	public String pluginToBeCalledWithCoil(int lineNumber,
			String[][] tableSequenze, String[][] tableCode) {
		if (tableSequenze == null)
			return null;
		if (tableCode == null)
			return null;
		String nome = null;
		if (TableSequence.getDone(tableSequenze, lineNumber).equals("0")) {
			for (int j2 = 0; j2 < tableCode.length; j2++) {
				boolean okCode = TableSequence.getCode(tableSequenze,
						lineNumber).equals(TableCode.getCode(tableCode, j2));
				boolean okCoil = false;
				if (TableCode.getCoil(tableCode, j2).equals("xxx")) {
					okCoil = true;
				} else {
					okCoil = TableSequence.getCoil(tableSequenze, lineNumber)
							.equals(TableCode.getCoil(tableCode, j2));
				}
				if (okCode && okCoil) {
					nome = "contMensili."
							+ TableCode.getPluginName(tableCode, j2);
					break;
				}
			}
		}

		return nome;
	}

	/**
	 * Cerca in tabella il numero delle linee in cui sono elencate le immagini
	 * da elaborare da parte del plugine e le mette in una stringa da passare
	 * come argomento al plugin. Potremo avere ad esempio 12#13#14#15 , se è
	 * previsto che quel determinato plugin processi 4 immagini
	 * 
	 * @param lineNumber
	 *            numero linea tableSequenze in elaborazione (la prima con
	 *            "fatto" =0
	 * @param tableSequenze5
	 *            tabella sequenze in memoria
	 * @return argomento per il plugin da chiamare
	 */
	public String argumentForPluginToBeCalled(int lineNumber,
			String[][] tableSequenze5) {

		String argomento = "";
		if (tableSequenze5 == null)
			return null;
		new TableSequence();
		int numImaDaPassare = Integer.parseInt(TableSequence.getImaPass(
				tableSequenze5, lineNumber));
		if (numImaDaPassare == 0) {
			return null;
		} else {
			for (int j2 = 0; j2 < numImaDaPassare; j2++) {
				if ((lineNumber + j2) >= tableSequenze5.length)
					return null;
				new TableSequence();
				argomento = argomento + "#"
						+ TableSequence.getRow(tableSequenze5, lineNumber + j2);
			}
		}
		return argomento;
	}

	/**
	 * Verifica che il numero di immagini rilevate da scanlist per un certo tipo
	 * di codice immagine corrisponda al numero previsto in iw2ayv.txt. Se ciò
	 * non accade viene passata una tabella di warnings a
	 * logVerifySequenceTable. Si può comunque continuare a lavorare
	 * 
	 * @param tableSequenze6
	 * @param tableCode6
	 * @return
	 */
	public String[][] verifySequenceTable(String[][] tableSequenze6,
			String[][] tableCode6) {

		// IJ.log("------- TableSequenze6 ------------");
		// TableUtils.dumpTable(tableSequenze6);
		// IJ.log("------- TableCode6 ------------");
		// TableUtils.dumpTable(tableCode6);

		if (tableSequenze6 == null) {
			MyLog.here("verifyList2.tableSequenze6 = null");
			return null;
		}
		if (tableCode6 == null) {
			MyLog.here("verifyList2.tableCode6 = null");
			return null;
		}
		List<String> vetCodice = new ArrayList<String>();
		List<String> vetCoil = new ArrayList<String>();
		List<String> vetImaRichieste = new ArrayList<String>();
		List<String> vetImaTrovate = new ArrayList<String>();
		List<String> vetPathImaAcquisite = new ArrayList<String>();
		List<String> vetSerieImaAcquisite = new ArrayList<String>();
		List<String> vetAcqImaAcquisite = new ArrayList<String>();
		List<String> vetImaImaAcquisite = new ArrayList<String>();
		String codiceBobinaAcquisito = "";
		for (int j1 = 0; j1 < tableCode6.length; j1++) {
			int numeroImaAcquisite = 0;
			int numeroImaRichieste = Integer.parseInt(TableCode.getImaTotal(
					tableCode6, j1));
			String codiceImaRichieste = TableCode.getCode(tableCode6, j1);
			if (codiceImaRichieste == null)
				break;
			if (numeroImaRichieste == 0)
				continue;
			String codiceBobinaRichiesto = TableCode.getCoil(tableCode6, j1);

			for (int j2 = 0; j2 < tableSequenze6.length; j2++) {
				new TableSequence();
				String codiceImaAcquisite = TableSequence.getCode(
						tableSequenze6, j2);
				codiceBobinaAcquisito = TableSequence.getCoil(tableSequenze6,
						j2);
				if (codiceImaAcquisite == null)
					break;
				if (compareAcqReq(codiceImaAcquisite, codiceImaRichieste,
						codiceBobinaAcquisito, codiceBobinaRichiesto)) {
					// MyLog.here("codiceAcquisito="+codiceBobinaAcquisito+" codiceRichiesto="+codiceBobinaRichiesto);
					numeroImaAcquisite++;

				}
			}
			if ((numeroImaAcquisite > 0)
					&& (numeroImaAcquisite != numeroImaRichieste)) {
				for (int j3 = 0; j3 < tableSequenze6.length; j3++) {
					new TableSequence();
					String codiceImaAcquisite2 = TableSequence.getCode(
							tableSequenze6, j3);
					codiceBobinaAcquisito = TableSequence.getCoil(
							tableSequenze6, j3);

					if (codiceImaAcquisite2 == null)
						break;
					int numeroImaAcquisite2 = 0;

					boolean codiceBobinaOK = false;
					if ((codiceBobinaRichiesto.equals(codiceBobinaAcquisito))
							|| (codiceBobinaRichiesto.equals("xxx")))
						codiceBobinaOK = true;

					if (codiceImaAcquisite2.equals(codiceImaRichieste)
							&& (codiceBobinaOK)) {
						numeroImaAcquisite2++;
						vetCodice.add(codiceImaRichieste);
						vetImaRichieste.add("" + numeroImaRichieste);
						vetImaTrovate.add("" + numeroImaAcquisite);
						vetPathImaAcquisite.add(TableSequence.getPath(
								tableSequenze6, j3));
						vetSerieImaAcquisite.add(TableSequence.getNumSerie(
								tableSequenze6, j3));
						vetAcqImaAcquisite.add(TableSequence.getNumAcq(
								tableSequenze6, j3));
						vetImaImaAcquisite.add(TableSequence.getNumIma(
								tableSequenze6, j3));
						vetCoil.add(TableSequence.getCoil(tableSequenze6, j3));
					}
				}
			}
		}
		// a questo punto non mi resta che creare la tabella e riversarvi i dati
		// dagli ArrayList
		if (vetCodice.size() > 0) {
			String[][] tableVuota = new String[vetCodice.size()][8];
			String[][] writeCol0 = writeTableColumn(tableVuota, vetCodice,
					TableVerify.CODE);
			String[][] writeCol1 = writeTableColumn(writeCol0, vetImaRichieste,
					TableVerify.IMA_REQUIRED);
			String[][] writeCol2 = writeTableColumn(writeCol1, vetImaTrovate,
					TableVerify.IMA_FOUND);
			String[][] writeCol3 = writeTableColumn(writeCol2,
					vetPathImaAcquisite, TableVerify.PATH);
			String[][] writeCol4 = writeTableColumn(writeCol3,
					vetSerieImaAcquisite, TableVerify.SERIE);
			String[][] writeCol5 = writeTableColumn(writeCol4,
					vetAcqImaAcquisite, TableVerify.ACQ);
			String[][] writeCol6 = writeTableColumn(writeCol5,
					vetImaImaAcquisite, TableVerify.IMA);
			String[][] tableOutput = writeTableColumn(writeCol6, vetCoil,
					TableVerify.COIL);
			return tableOutput;
		} else {
			return null;
		}
	}

	/**
	 * Verifica che il numero di immagini rilevate da scanlist per un certo tipo
	 * di codice immagine corrisponda al numero previsto in iw2ayv.txt. Se ciò
	 * non accade viene passata una tabella di warnings a
	 * logVerifySequenceTable. Si può comunque continuare a lavorare
	 * 
	 * @param tableSequenze6
	 * @param tableCode6
	 * @return
	 */
	public String[][] verifySequenceTableOLD(String[][] tableSequenze6,
			String[][] tableCode6) {

		TableUtils.dumpTable(tableSequenze6);
		TableUtils.dumpTable(tableCode6);

		if (tableSequenze6 == null) {
			MyLog.here("verifyList2.tableSequenze6 = null");
			return null;
		}
		if (tableCode6 == null) {
			MyLog.here("verifyList2.tableCode6 = null");
			return null;
		}
		List<String> vetCodice = new ArrayList<String>();
		List<String> vetCoil = new ArrayList<String>();
		List<String> vetImaRichieste = new ArrayList<String>();
		List<String> vetImaTrovate = new ArrayList<String>();
		List<String> vetPathImaAcquisite = new ArrayList<String>();
		List<String> vetSerieImaAcquisite = new ArrayList<String>();
		List<String> vetAcqImaAcquisite = new ArrayList<String>();
		List<String> vetImaImaAcquisite = new ArrayList<String>();
		for (int j1 = 0; j1 < tableCode6.length; j1++) {
			int numeroImaAcquisite = 0;
			int numeroImaRichieste = Integer.parseInt(TableCode.getImaTotal(
					tableCode6, j1));
			String codiceImaRichieste = TableCode.getCode(tableCode6, j1);
			if (codiceImaRichieste == null)
				break;
			if (numeroImaRichieste == 0)
				continue;
			String codiceBobinaRichiesto = TableCode.getCoil(tableCode6, j1);
			for (int j2 = 0; j2 < tableSequenze6.length; j2++) {
				new TableSequence();
				String codiceImaAcquisite = TableSequence.getCode(
						tableSequenze6, j2);
				String codiceBobinaAcquisito = TableSequence.getCoil(
						tableSequenze6, j2);
				if (codiceImaAcquisite == null)
					break;
				if (compareAcqReq(codiceImaAcquisite, codiceImaRichieste,
						codiceBobinaAcquisito, codiceBobinaRichiesto)) {
					numeroImaAcquisite++;
				}
			}
			if ((numeroImaAcquisite > 0)
					&& (numeroImaAcquisite != numeroImaRichieste)) {
				for (int j3 = 0; j3 < tableSequenze6.length; j3++) {
					new TableSequence();
					String codiceImaAcquisite2 = TableSequence.getCode(
							tableSequenze6, j3);
					if (codiceImaAcquisite2 == null)
						break;
					int numeroImaAcquisite2 = 0;
					if (codiceImaAcquisite2.equals(codiceImaRichieste)) {
						numeroImaAcquisite2++;
						vetCodice.add(codiceImaRichieste);
						vetImaRichieste.add("" + numeroImaRichieste);
						vetImaTrovate.add("" + numeroImaAcquisite);
						vetPathImaAcquisite.add(TableSequence.getPath(
								tableSequenze6, j3));
						vetSerieImaAcquisite.add(TableSequence.getNumSerie(
								tableSequenze6, j3));
						vetAcqImaAcquisite.add(TableSequence.getNumAcq(
								tableSequenze6, j3));
						vetImaImaAcquisite.add(TableSequence.getNumIma(
								tableSequenze6, j3));
						vetCoil.add(TableSequence.getCoil(tableSequenze6, j3));
					}
				}
			}
		}
		// a questo punto non mi resta che creare la tabella e riversarvi i dati
		// dagli ArrayList
		if (vetCodice.size() > 0) {
			String[][] tableVuota = new String[vetCodice.size()][8];
			String[][] writeCol0 = writeTableColumn(tableVuota, vetCodice,
					TableVerify.CODE);
			String[][] writeCol1 = writeTableColumn(writeCol0, vetImaRichieste,
					TableVerify.IMA_REQUIRED);
			String[][] writeCol2 = writeTableColumn(writeCol1, vetImaTrovate,
					TableVerify.IMA_FOUND);
			String[][] writeCol3 = writeTableColumn(writeCol2,
					vetPathImaAcquisite, TableVerify.PATH);
			String[][] writeCol4 = writeTableColumn(writeCol3,
					vetSerieImaAcquisite, TableVerify.SERIE);
			String[][] writeCol5 = writeTableColumn(writeCol4,
					vetAcqImaAcquisite, TableVerify.ACQ);
			String[][] writeCol6 = writeTableColumn(writeCol5,
					vetImaImaAcquisite, TableVerify.IMA);
			String[][] tableOutput = writeTableColumn(writeCol6, vetCoil,
					TableVerify.COIL);
			return tableOutput;
		} else {
			return null;
		}
	}

	/***
	 * Verifica che i codici di bobina e sequenza richiesti ed effettivi siano
	 * uguali
	 * 
	 * @param codeImaAcq
	 * @param codeImaReq
	 * @param coilImaAcq
	 * @param coilImaReq
	 * @return
	 */
	public boolean compareAcqReq(String codeImaAcq, String codeImaReq,
			String coilImaAcq, String coilImaReq) {
		boolean res1;
		if (codeImaAcq.equals(codeImaReq)) {
			if (coilImaReq.equals("xxx") || coilImaReq.equals("XXX")) {
				res1 = true;
			} else if (coilImaAcq.equals(coilImaReq)) {
				res1 = true;
			} else {
				res1 = false;
			}
		} else {
			res1 = false;
		}
		return res1;
	}

	/**
	 * Riceve da verifySequenceTable una tabella di codici a cui non corrisponde
	 * l'esatto numero di immagini previste. Stampa a log i warnings.
	 * 
	 * @param tableVerify
	 */
	public void logVerifySequenceTable(String[][] tableVerify) {

		if (tableVerify == null)
			return;
		// TableUtils.dumpTable(tableVerify, "tableVerify");
		IJ.showMessage("Problemi con le immagini, vedere il log");
		String codice2 = "";
		String coil2 = "";
		boolean stamp = false;
		IJ.log("---------------------------------------------");
		for (int i1 = 0; i1 < tableVerify.length; i1++) {
			String codice1 = TableVerify.getCode(tableVerify, i1);
			String coil1 = TableVerify.getCoil(tableVerify, i1);
			int numero = Integer.parseInt(TableVerify.getImaFound(tableVerify,
					i1));
			if ((!codice1.equals(codice2)) || (!coil1.equals(coil2))) {
				stamp = true;
				IJ.log("per il codice " + codice1 + " e la bobina " + coil1
						+ " sono necessarie "
						+ TableVerify.getImaRequired(tableVerify, i1)
						+ " immagini, trovate " + numero);

				codice2 = codice1;
				coil2 = coil1;
				if ((i1 + numero) <= tableVerify.length) {
					for (int i2 = i1; i2 < i1 + numero; i2++) {

						IJ.log(TableVerify.getPath(tableVerify, i2) + " ser:"
								+ TableVerify.getSerie(tableVerify, i2)
								+ " acq:" + TableVerify.getAcq(tableVerify, i2)
								+ " ima:" + TableVerify.getIma(tableVerify, i2)
								+ " coil:"
								+ TableVerify.getCoil(tableVerify, i2));
					}
				}
			}
			if (stamp) {
				IJ.log("---------------------------------------------");
				stamp = false;
			}
		}

	}

	/**
	 * effettua il riordino della tabella dati effettuando dapprima un sort in
	 * base ad AcquisitionTime e poi estraendo il risultato in base all'ordine
	 * di codici.txt
	 * 
	 * @param tableSequenze
	 *            tabella da riordinare
	 * @return tabella riordinata
	 */
	public String[][] reorderSequenceTable(String[][] tableSequenze,
			String[][] tableCodici) {

		ArrayList<ArrayList<String>> matrixTableReordered = new ArrayList<ArrayList<String>>();

		if (tableSequenze == null) {
			MyLog.here("reorderSequenceTable.tableSequenze = null");
			return null;
		}
		if (tableCodici == null) {
			MyLog.here("reorderSequenceTable.tableCode = null");
			return null;
		}
		// ora effettuo l'estrazione dei codici dalla tabella, secondo
		// l'ordine di codici.txt
		int count = -1;
		for (int i1 = 0; i1 < tableCodici.length; i1++) {
			for (int i2 = 0; i2 < tableSequenze.length; i2++) {
				if (tableSequenze[i2][TableSequence.CODE] == null)
					IJ.log("reorderSequenceTable.NULL");
				else if (compareAcqReq(tableSequenze[i2][TableSequence.CODE],
						tableCodici[i1][TableCode.CODE],
						tableSequenze[i2][TableSequence.COIL],
						tableCodici[i1][TableCode.COIL])) {
					count++;
					ArrayList<String> row = new ArrayList<String>();
					row.add("" + count);
					for (int i3 = 1; i3 < tableSequenze[0].length; i3++) {
						row.add(tableSequenze[i2][i3]);
					}
					matrixTableReordered.add(row);
				}
			}
		}
		String[][] tableSequenzeReordered = new InputOutput()
				.fromArrayListToStringTable(matrixTableReordered);
		return (tableSequenzeReordered);
	}

	/**
	 * Effettua il bubble sort della tabella delle sequenze, utilizza
	 * l'algoritmo bubblesort
	 * 
	 * @param tableIn
	 * @return
	 */
	public String[][] bubbleSortSequenceTable(String[][] tableIn) {

		if (tableIn == null) {
			IJ.log("bubbleSortTable.tableIn == null");
			return null;
		}
		long[] bubblesort = new long[tableIn.length];
		String[][] tableOut = new TableUtils().duplicateTable(tableIn);
		for (int i1 = 0; i1 < tableOut.length; i1++) {
			String acqTime = TableSequence.getAcqTime(tableOut, i1);
			if (acqTime == null)
				acqTime = "9999999999999999";
			bubblesort[i1] = Long.parseLong(acqTime);
		}
		String[] tempRiga = new String[tableOut[0].length];
		boolean sorted = false;
		while (!sorted) {
			sorted = true;
			for (int i1 = 0; i1 < (bubblesort.length - 1); i1++) {
				if (bubblesort[i1] > bubblesort[i1 + 1]) {
					long temp = bubblesort[i1];
					// N.B. i2 in questo caso partirà da 1, poichè la colonna 0
					// che contiene il numero della riga NON deve venire sortata
					for (int i2 = 1; i2 < tableOut[0].length; i2++)
						tempRiga[i2] = tableOut[i1][i2];
					bubblesort[i1] = bubblesort[i1 + 1];
					for (int i2 = 1; i2 < tableOut[0].length; i2++)
						tableOut[i1][i2] = tableOut[i1 + 1][i2];
					bubblesort[i1 + 1] = temp;
					for (int i2 = 1; i2 < tableOut[0].length; i2++)
						tableOut[i1 + 1][i2] = tempRiga[i2];
					sorted = false;
				}
			}
		}
		return tableOut;
	}

	/**
	 * Verifica l'esistenza di un file *.jar
	 * 
	 * @param source
	 * @return
	 */
	public boolean checkJar(String source) {
		URL url1 = this.getClass().getResource("/" + source);
		if (url1 != null)
			return true;
		else
			return false;
	}

} // ultima
