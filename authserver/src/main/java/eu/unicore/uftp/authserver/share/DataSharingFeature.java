package eu.unicore.uftp.authserver.share;

import eu.unicore.services.Kernel;
import eu.unicore.services.rest.RestService;
import eu.unicore.services.utils.deployment.DeploymentDescriptorImpl;
import eu.unicore.services.utils.deployment.FeatureImpl;

/**
 * job execution
 * 
 * @author schuller
 */
public class DataSharingFeature extends FeatureImpl {

	public DataSharingFeature() {
		this.name = "DataSharing";
		services.add(new ShareSD(kernel));
		services.add(new AccessSD(kernel));
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
