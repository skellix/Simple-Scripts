package com.simplescript;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Alex on 4/25/14.
 */
public abstract class ErrOutputGrabber {

	//

	public ErrOutputGrabber(byte[] bytes) {
		PrintStream err = System.err;
		try {
			PipedOutputStream pipedOutputStream = new PipedOutputStream();
			System.setErr(new PrintStream(pipedOutputStream));
			PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream);
			Scanner scanner = new Scanner(pipedInputStream);
			Thread newThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						ErrOutputGrabber.this.run();
					} catch (Exception e) {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
						e.printStackTrace();
					}
				}
			});
			newThread.start();
			try {
				newThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (scanner.hasNextLine()) {
				String out = "";
				while (scanner.hasNextLine()) {
					out += scanner.nextLine() + "\n";
				}
				String location = "";
				Matcher matcher = Pattern.compile("Location\\:\\s+([^\n]+)").matcher(out);
				if (matcher.find()) {
					location = matcher.group(1);
				}
				String reason = "";
				matcher = Pattern.compile("Reason\\:\\s+([^\n]+)").matcher(out);
				if (matcher.find()) {
					reason = matcher.group(1);
				}
				System.out.println(reason + " at:\n" + location);
				/*if (bytes != null) {
					ClassReader classReader = new ClassReader(bytes);
					PipedOutputStream asmifierStream = new PipedOutputStream();
					PipedInputStream asmifierInputStream = new PipedInputStream(asmifierStream);
					Scanner asmifierScanner = new Scanner(asmifierInputStream);
					classReader.accept(
							new TraceClassVisitor(
									null,
									new ASMifier(),
									new PrintWriter(System.out)
							),
							ClassReader.SKIP_DEBUG
					);
					while (asmifierScanner.hasNextLine()) {
						System.out.println(asmifierScanner.nextLine());
					}
					asmifierScanner.close();
				}//*/
			}
			scanner.close();
			pipedOutputStream.flush();
			pipedOutputStream.close();
			err.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.setErr(err);
	}

	public abstract void run() throws Exception;
}
