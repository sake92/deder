package ba.sake.deder.client;

public interface DederClient {
	void start() throws Exception;

	void stop(boolean isCancel) throws Exception;
}
