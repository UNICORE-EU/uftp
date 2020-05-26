package eu.unicore.uftp.authserver.share;

import java.util.ArrayList;
import java.util.List;

import de.fzj.unicore.wsrflite.DeploymentDescriptor;
import de.fzj.unicore.wsrflite.Kernel;
import de.fzj.unicore.wsrflite.utils.deployment.DeploymentDescriptorImpl;
import de.fzj.unicore.wsrflite.utils.deployment.FeatureImpl;
import eu.unicore.services.rest.RestService;

/**
 * job execution
 * 
 * @author schuller
 */
public class DataSharingFeature extends FeatureImpl {

	public DataSharingFeature(Kernel kernel) {
		this();
		setKernel(kernel);
	}
	
	public DataSharingFeature() {
		this.name = "DataSharing";
	}

	public List<DeploymentDescriptor> getServices(){
		List<DeploymentDescriptor> services = new ArrayList<>();
		services.add(new ShareSD(kernel));
		services.add(new AccessSD(kernel));
		return services;
	}
	
	
	public static class ShareSD extends DeploymentDescriptorImpl {

		public ShareSD(Kernel kernel){
			this();
			setKernel(kernel);
		}
		
		public ShareSD() {
			super();
			this.name = "share";
			this.type = RestService.TYPE;
			this.implementationClass = ShareService.class;
		}
	}
	
	public static class AccessSD extends DeploymentDescriptorImpl {

		public AccessSD(Kernel kernel){
			this();
			setKernel(kernel);
		}
		
		public AccessSD() {
			super();
			this.name = "access";
			this.type = RestService.TYPE;
			this.implementationClass = AccessService.class;
		}
	}

}
