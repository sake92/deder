package ba.sake.deder.client.cli;

import java.util.function.Consumer;
import java.io.*;
import java.util.concurrent.TimeUnit;

class SubprocessRunningThread extends Thread {

	private final String[] command;
	private final Consumer<String> log;
	private Process runningSubprocess = null;
    private int exitCode = -1; // -1 means unknown

	public SubprocessRunningThread(String[] command, Consumer<String> log) {
        super("DederCliSubprocessRunningThread");
		this.command = command;
		this.log = log;
	}

	@Override
	public void run() {
		try {
			var processBuilder = new ProcessBuilder(command);
			processBuilder.inheritIO();
			runningSubprocess = processBuilder.start();
			runningSubprocess.waitFor();
			exitCode = runningSubprocess.exitValue();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (InterruptedException e1) {
			if (runningSubprocess != null) {
				log.accept("Killing previous subprocess...");
				runningSubprocess.destroy();
				if (runningSubprocess.isAlive()) {
					try {
						runningSubprocess.waitFor(5, TimeUnit.SECONDS);
					} catch (InterruptedException e2) {
						// ignore
					} finally {
						if (runningSubprocess.isAlive()) {
							log.accept("Forcibly killing previous subprocess...");
							runningSubprocess.destroyForcibly();
						}
					}
				}
			}
		}
	}

    public int getExitCode() {
        return exitCode;
    }
}
