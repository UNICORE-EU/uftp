package eu.unicore.uftp.authserver.share;

import eu.unicore.services.Kernel;
import eu.unicore.services.rest.RestService;
import eu.unicore.services.utils.deployment.DeploymentDescriptorImpl;
import eu.unicore.services.utils.deployment.FeatureImpl;

/**
 * deploys the data sharing and access services
 *
 * @author schuller
 */
public class DataSharingFeature extends FeatureImpl {

	public DataSharingFeature() {
		this.name = "DataSharing";
	}

	@Override
	public void setKernel(Kernel kernel) {
		super.setKernel(kernel);
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
