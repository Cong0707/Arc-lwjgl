package arc.graphics.vk;

/**
 * Backend/runtime information for native Vulkan implementations.
 */
public class VkBackendInfo{
    public final String backendName;
    public final String apiVersion;
    public final String driver;
    public final String vendor;
    public final String renderer;

    public VkBackendInfo(String backendName, String apiVersion, String driver, String vendor, String renderer){
        this.backendName = backendName;
        this.apiVersion = apiVersion;
        this.driver = driver;
        this.vendor = vendor;
        this.renderer = renderer;
    }
}
