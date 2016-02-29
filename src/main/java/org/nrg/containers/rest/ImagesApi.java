package org.nrg.containers.rest;

import io.swagger.annotations.ApiParam;
import org.apache.commons.lang.StringUtils;
import org.nrg.containers.exceptions.BadRequestException;
import org.nrg.containers.exceptions.NoServerPrefException;
import org.nrg.containers.exceptions.NotFoundException;
import org.nrg.containers.model.ContainerServer;
import org.nrg.containers.model.Image;
import org.nrg.containers.model.ImageParameters;
import org.nrg.containers.services.ContainerService;
import org.nrg.prefs.exceptions.InvalidPreferenceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.inject.Inject;
import java.util.List;

//@Api(description = "XCS images API")
@RestController
@RequestMapping(value = "/images", produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
public class ImagesApi {
    private static final Logger _log = LoggerFactory.getLogger(ImagesApi.class);

//    @ApiOperation(value = "Get list of images.", notes = "Returns a list of all images on the container server.", response = Image.class, responseContainer = "List")
//    @ApiResponses({
//            @ApiResponse(code = 200, message = "A list of images on the server"),
//            @ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<List<Image>> getAllImages() {
        try {
            return new ResponseEntity<List<Image>>(service.getAllImages(), HttpStatus.OK);
        } catch (NoServerPrefException e) {
            // TODO This exception handling sucks. Fix it.
            return new ResponseEntity<>(HttpStatus.FAILED_DEPENDENCY);
        }
    }

    //    @ApiOperation(value = "Get list of images.", notes = "Returns a list of all images on the container server.", response = Image.class, responseContainer = "List")
//    @ApiResponses({
//            @ApiResponse(code = 200, message = "A list of images on the server"),
//            @ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(method = RequestMethod.GET, params = {"name"})
    public ResponseEntity<Image> getByName(final @RequestParam String name) throws NotFoundException {
        try {
            final Image image = service.getImageByName(name);
            if (image == null) {
                throw new NotFoundException("No image found with name "+name);
            }
            return new ResponseEntity<Image>(image, HttpStatus.OK);
        } catch (NoServerPrefException e) {
            // TODO This exception handling sucks. Fix it.
            return new ResponseEntity<>(HttpStatus.FAILED_DEPENDENCY);
        }

    }

    @RequestMapping(method = RequestMethod.GET, params = {"id"})
    public ResponseEntity<Image> getById(final @RequestParam String id) throws NotFoundException {
        try {

            final Image image = service.getImageById(id);
            if (image == null) {
                throw new NotFoundException("No image found with id " + id);
            }
            return new ResponseEntity<Image>(image, HttpStatus.OK);
        } catch (NoServerPrefException e) {
            // TODO This exception handling sucks. Fix it.
            return new ResponseEntity<>(HttpStatus.FAILED_DEPENDENCY);
        }
    }

    @RequestMapping(method = RequestMethod.DELETE, params = {"name"})
    public ResponseEntity<String> deleteByName(final @RequestParam String name) throws NotFoundException {
        try {
            final String id = service.deleteImageByName(name);
            if (id == null) {
                throw new NotFoundException("No image found with name " + name);
            }
            return new ResponseEntity<>(id, HttpStatus.OK);
        } catch (NoServerPrefException e) {
            // TODO This exception handling sucks. Fix it.
            return new ResponseEntity<>(HttpStatus.FAILED_DEPENDENCY);
        }
    }

    @RequestMapping(method = RequestMethod.DELETE, params = {"id"})
    public ResponseEntity<String> deleteById(final @RequestParam(name="id") String inputId) throws NotFoundException {
        try {

            final String id = service.deleteImageById(inputId);
            if (id == null) {
                throw new NotFoundException("No image found with id " + inputId);
            }
            return new ResponseEntity<>(id, HttpStatus.OK);
        } catch (NoServerPrefException e) {
            // TODO This exception handling sucks. Fix it.
            return new ResponseEntity<>(HttpStatus.FAILED_DEPENDENCY);
        }
    }

    @RequestMapping(method = RequestMethod.DELETE, params = {})
    @ResponseBody
    public void delete() throws BadRequestException {
        throw new BadRequestException("Include the name or id of an image to delete in the query parameters.");
    }

//    @ApiOperation(value = "Launches a container.", notes = "Returns the updated serialized image object with the specified image name.", response = Image.class)
//    @ApiResponses({
//            @ApiResponse(code = 200, message = "Image successfully created or updated."),
//            @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."),
//            @ApiResponse(code = 403, message = "Not authorized to create or update this image."),
//            @ApiResponse(code = 404, message = "Image not found."),
//            @ApiResponse(code = 500, message = "Unexpected error")})
    @RequestMapping(value = {"/launch"}, method = {RequestMethod.POST}, params = {"name"})
    public ResponseEntity<String> launch(@ApiParam(value = "The name of the image to launch.", required = true) @RequestParam("name") String name, @RequestBody ImageParameters launchArguments) {
        final String containerId;
        try {
            containerId = service.launch(name, launchArguments);
            return StringUtils.isBlank(containerId) ?
                    new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR) :
                    new ResponseEntity<>(containerId, HttpStatus.OK);
        } catch (NoServerPrefException e) {
            // TODO This exception handling sucks. Fix it.
            return new ResponseEntity<>(HttpStatus.FAILED_DEPENDENCY);
        }
    }

    @RequestMapping(value = {"/server"}, method = {RequestMethod.GET})
    public ResponseEntity<ContainerServer> getServer() {
        try {
            return new ResponseEntity<>(service.getServer(), HttpStatus.OK);
        } catch (NoServerPrefException e) {
            // TODO This exception handling sucks. Fix it.
            return new ResponseEntity<>(HttpStatus.FAILED_DEPENDENCY);
        }
    }

    @RequestMapping(value = {"/server"}, method = {RequestMethod.POST})
    public ResponseEntity setServer(@RequestBody final ContainerServer containerServer) {
        try {
            service.setServer(containerServer.host());
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (NoServerPrefException e) {
            // TODO This exception handling sucks. Fix it.
            return new ResponseEntity<>(HttpStatus.FAILED_DEPENDENCY);
        } catch (InvalidPreferenceName invalidPreferenceName) {
            // TODO Do something with this
            invalidPreferenceName.printStackTrace();
        }
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Inject
    @SuppressWarnings("SpringJavaAutowiringInspection") // IntelliJ does not process the excludeFilter in ContainersSpringConfig @ComponentScan, erroneously marks this red
    private ContainerService service;
}
