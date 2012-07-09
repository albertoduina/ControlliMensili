package contMensili;

import utils.AboutBox;
import utils.MyLog;
import ij.IJ;
import ij.plugin.PlugIn;

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
 * Il Plugin version- restituisce i numeri di versione impostati per i file
 * sorgente
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */
public class version_ implements PlugIn {
	public static String VERSION = "version_v4.10_10dec08_";

	// static Dialog mioDialogo;
	public void run(String args) {

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

		IJ.log("--------------------------");
		IJ.log("Package contMensili VERSION "
				+ new AboutBox().myImplementationVersion(this.getClass()));
		IJ.log("--------------------------");
		IJ.log(Sequenze_.VERSION + "                      programma principale");
		IJ.log(p2rmn_.VERSION + "                         calcolo T1 e T2");
		IJ.log(p3rmn_.VERSION
				+ "                         uniformità, snr, ghost su immagini 'tonde'");
		IJ.log(p4rmn_.VERSION + "                          calcolo mtf");
		IJ.log(p5rmn_.VERSION
				+ "                          uniformità immagini 'piatte'");
		IJ.log(p6rmn_.VERSION
				+ "                          calcolo spessore fetta");
		IJ.log(p7rmn_.VERSION + "                         calcolo warp");
		IJ.log(p8rmn_.VERSION + "                         calcolo DGP");
		IJ.log(p9rmn_.VERSION + "                         calcolo CNR");
		IJ.log("--------------------------");
		findIt("utils.IW2AYV");

	}

	public static void findIt(String classname) {

		try {
			String classpath = System.getProperty("java.class.path", "");
			MyLog.waitHere("classpath= " + classpath);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
