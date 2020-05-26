package eu.unicore.uftp.authserver;

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
public class AuthServerFeature extends FeatureImpl {

	public AuthServerFeature(Kernel kernel) {
		this();
		setKernel(kernel);
	}
	
	public AuthServerFeature() {
		this.name = "AuthServer";
	}

	public List<DeploymentDescriptor> getServices(){
		List<DeploymentDescriptor> services = new ArrayList<>();
		services.add(new AuthSD(kernel));		
		return services;
	}
	
	
	public static class AuthSD extends DeploymentDescriptorImpl {

		public AuthSD(Kernel kernel){
			this();
			setKernel(kernel);
		}
		
		public AuthSD() {
			super();
			this.name = "auth";
			this.type = RestService.TYPE;
			this.implementationClass = AuthService.class;
		}
	}

}
