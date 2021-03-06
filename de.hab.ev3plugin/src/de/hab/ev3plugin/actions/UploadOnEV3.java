/**
 * \file UploadOnEV3.java
 * Callback for the menu entry that uploads to ev3
 */
package de.hab.ev3plugin.actions;

import ilg.gnuarmeclipse.managedbuild.cross.ProjectStorage;

import java.io.File;
import java.util.Hashtable;

import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import de.hab.ev3plugin.Assembler;
import de.hab.ev3plugin.Ev3Duder;
import de.hab.ev3plugin.Preprocessor;
import de.hab.ev3plugin.gui.UploadProgress;
import de.hab.ev3plugin.util.Gui;
import de.hab.ev3plugin.util.IO;

public class UploadOnEV3 implements IWorkbenchWindowActionDelegate {

	protected IWorkbenchWindow window;
	protected String projectRoot;
	protected String projectName;
	protected IManagedBuildInfo buildInfo;
	protected String binaryDir;
	protected String workspaceRoot; // get location of workspace

	protected String localBinary;
		String remoteBinary;
		protected String localLauncher;
	protected String remoteLauncher;
	protected boolean windows;
	protected String ev3duder_binname;		
	protected Ev3Duder ev3duder;
	protected Shell shell;
	protected UploadProgress dialog;

	public void postUpload()
	{
	}

	@Override
	public void run(IAction action) {

		try {
			IProject proj = Gui.getActiveProject(); // adapter.getProject()
				
			if (proj == null) {
				MessageDialog.openInformation(window.getShell(), "Lego EV3",
						"Select the project you want to upload first.");
				return;
			}
			
			projectRoot = proj.getLocation().toString();
			projectName = proj.getName();
			buildInfo = ManagedBuildManager.getBuildInfo(proj);
			
			binaryDir = buildInfo.getDefaultConfiguration().getName();
			workspaceRoot = ResourcesPlugin.getWorkspace().getRoot()
					.getLocation().toString(); // get location of workspace

			localBinary = projectRoot + "/" + binaryDir + "/" + projectName + ".elf";
				String remoteBinary = "/media/card/" + projectName + ".elf";
			localLauncher = projectRoot + "/myapps/" + projectName + ".rbf";
			remoteLauncher = "/media/card/myapps/" + projectName + ".rbf";
			windows = System.getProperty("os.name").startsWith("Windows");
			ev3duder_binname = windows ? "ev3duder.exe" : "ev3duder";
			shell = ilg.gnuarmeclipse.managedbuild.cross.Activator.getDefault()
					.getWorkbench().getActiveWorkbenchWindow().getShell();
			dialog = new UploadProgress(shell);
			boolean nowait = true; // TODO: add to .lms define nowait
			// Display display = Display.getDefault();
			// Progress dialog = new Progress(new Shell(display, SWT.MODELESS));
			dialog.setBlockOnOpen(false);
			dialog.open();
	
			if (!proj.exists())
				return; //FIXME: needed?!

			IConfiguration[] configs = buildInfo.getManagedProject()
					.getConfigurations();
	
        /*	MessageDialog.openInformation(window.getShell(), "uploader=",
					ProjectStorage.getValue(configs[0], "uploader"));
		*/
			String path = ProjectStorage.getValue(configs[0], "uploader");
			File uploader = null;
			File assembler = null;
			ev3duder = null;	
			
			if (path != null) {
				uploader = new File(path, ev3duder_binname); // externalise this!
				assembler = new File(path, "assembler.jar"); // check for its dependencies!
			}
			if (uploader == null || !uploader.exists() || uploader.isDirectory())
			{

			}
			else
			{
				ev3duder = new Ev3Duder(uploader.toString(), shell);
			}
			do {	
				dialog.setProgress(20, "Assembling starter..");
				dialog.setProgress(20, "Assembling starter..");

				if (assembler == null || !assembler.exists() || assembler.isDirectory()) {
				//	MessageDialog.openWarning(shell, "Assembling failed","Either the <assembler.jar> file couldn't be found in "+ path +
				//		" or the project has no <start.lms>. Default values will be used."); // change warnings to write to log!

					if (ev3duder == null)
					{
						MessageDialog.openWarning(shell, "Uploader not found",
								"Ev3duder couldn't be found in " + path + "!");
						break; // no assembler + no uploader = nothing to do
					}
					if (!ev3duder.command("mkrbf", "/media/card/" + projectName + ".elf", localLauncher))
					{
						MessageDialog.openWarning(shell, "Mkrbf failed",
								"Ev3duder couldn't mkrbf :( ");
						break;	
					}
						
					remoteBinary = "/media/card/" + projectName + ".elf";
				}else
				{
                    Hashtable<String, String> map = new Hashtable<String, String>();
                    map.put("${projectName}", projectName);
                    map.put("${card}", "/media/card");
                    map.put("${usb}", "/media/usb");
                    map.put("${brick}", "/home/root/lms2012/prjs/BrkProg_SAVE");
                    dialog.log("Preprocessing start.lms");
                    Preprocessor pp = new Preprocessor(projectRoot + "/start.lms");
                    String temp_starter = IO.removeExtension(pp.run(map).getAbsolutePath());
                    dialog.log(" [DONE]\n");
                    
                    //Assembler asm = new Assembler(temp_starter, uploader);
                    dialog.log("Generating rbf: " + temp_starter);
                    System.out.println("temp_starter=" + temp_starter);
                    (new Assembler(temp_starter, uploader.getParentFile())).run();
                    //asm.run();

                    IO.copy(new File(temp_starter + ".rbf"), new File(localLauncher));
                    dialog.log(" [DONE]\n");
                    
                    // Now let's get upload params
                    Hashtable<String, String> defines = pp.defines();
                    remoteBinary = defines.get("elfexec");
                    dialog.log("remoteBinary='" + remoteBinary + "'\n");
                    remoteLauncher = defines.get("starter");
                    dialog.log("remoteLauncher='" + remoteLauncher + "'\n"); 
                    
			}	
				
				/* left for illustratory purposes.
				 * ev3duder up already creates directories
				// attempt creation of a /myapps/ directory, if it's already there, no harm done.
				ev3duder.setSilent(true);
				dialog.setProgress(40, "Attempting to create directory");
				dialog.setProgress(40, "Attempting to create directory");
				ev3duder.command("mkdir", IO.getParent(remoteLauncher));
				ev3duder.setSilent(false);
				*/
				dialog.setProgress(60, "Uploading ELF executable..");
				dialog.setProgress(60, "Uploading ELF executable..");

				if (!ev3duder.transferFile(localBinary, remoteBinary))
					break;
				if (!nowait) Thread.sleep(300);

				dialog.setProgress(80, "Uploading starter file..");
				dialog.setProgress(80, "Uploading starter file..");
				if (!ev3duder.transferFile(localLauncher, remoteLauncher)) {
					MessageDialog.openWarning(shell, "Uploading launcher failed",
							" no idea. sorry ."); // change warnings to write to log!
					break;
				}

				postUpload(); // OO Baby steps
}while(false); // a legitimate use for goto IMHO
if (ev3duder == null)
{
	//TODO: remove connect on mkrbf
		MessageDialog.openError(shell, "Uploader not found", "The project's uploader path <"
								+ path +
								"> is invalid. Try correcting it");
}
			//if (!nowait) Thread.sleep(1000);
			dialog.close();
			} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		// TODO Auto-generated method stub

	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public void init(IWorkbenchWindow window) {

		this.window = window;

	}
}
