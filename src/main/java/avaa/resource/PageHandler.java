package avaa.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/")
public class PageHandler {

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_PLAIN) 
    public String home_page(){
        return "this is home";
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN) 
    @Path("/publish")
    public String publish_page(){
        return "this is publish";
    }


    @GET
    @Path("/metadata")
    @Produces(MediaType.TEXT_PLAIN) 
    public String metadata_page(){
        return "this is metadata";
    }

    @GET
    @Path("/help")
    @Produces(MediaType.TEXT_PLAIN) 
    public String help_page(){
        return "this is help page";
    }


    @GET
    @Path("/ftp-/-rsync")
    @Produces(MediaType.TEXT_PLAIN) 
    public String ftp_page(){
        return "this is ftp page";
    }

    @GET
    @Path("/latauspalveu")
    @Produces(MediaType.TEXT_PLAIN) 
    public String download_page(){
        return "this is download page";
    }

    @GET
    @Path("/contact")
    @Produces(MediaType.TEXT_PLAIN) 
    public String contact_page(){
        return "this is contact page";
    }

    @GET
    @Path("/rajapinta")
    @Produces(MediaType.TEXT_PLAIN) 
    public String cAPI_page(){
        return "this is API page";
    }
}
