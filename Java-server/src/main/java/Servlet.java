import com.google.gson.Gson;

import javax.servlet.*;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * @author xiaorui
 */
@MultipartConfig
public class Servlet extends HttpServlet {
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String FIXED_ALBUM_KEY = "1";
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("application/json");
        String urlPath = req.getPathInfo();

        // check have a URL
        if (urlPath == null || urlPath.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            ErrorMsg errorMsg = new ErrorMsg("URL path is invalid.");
            res.getWriter().write(new Gson().toJson(errorMsg));
            return;
        }

        // GET: /albums/{albumID}
        String[] urlPaths = urlPath.split("/");
        if (!isUrlValid(urlPaths, "GET")) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            ErrorMsg errorMsg = new ErrorMsg("URL format is invalid.");
            res.getWriter().write(new Gson().toJson(errorMsg));
            return;
        } else {
            AlbumInfo albumInfo = new AlbumInfo("Sex Pistols", "Never Mind The Bollocks!", "1977");
            res.setStatus(HttpServletResponse.SC_OK);
            res.getWriter().write(new Gson().toJson(albumInfo));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("application/json");

        // check if the request has multipart
        if (!req.getContentType().toLowerCase(Locale.ENGLISH).startsWith("multipart/")) {
            ErrorMsg errorMsg = new ErrorMsg("Form must have enctype=multipart/form-data.");
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            res.getWriter().write(new Gson().toJson(errorMsg));
            return;
        }

        // check URL format
        String urlPath = req.getPathInfo();
        if (urlPath != null) {
            ErrorMsg errorMsg = new ErrorMsg("URL format is invalid.");
            res.getWriter().write(new Gson().toJson(errorMsg));
            return;
        }

        // process multipart request
        try {
            Part profilePart = req.getPart("profile");
            if (profilePart != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(profilePart.getInputStream()))) {
                    String profileJson = reader.lines().collect(Collectors.joining());
                    AlbumInfo profile = new Gson().fromJson(profileJson, AlbumInfo.class);
                }
            }

            Part imagePart = req.getPart("image");
            String image = null;
            if (imagePart != null) {
                try (InputStream inputStream = imagePart.getInputStream()) {
                    image = Base64.getEncoder().encodeToString(inputStream.readAllBytes());
                }
            }

            // calculate the size of the image
            int base64StringLength = image != null ? image.length() : 0;
            int imageSize = getImageSize(image, base64StringLength);

            ImageMetaData imageMetaData = new ImageMetaData(FIXED_ALBUM_KEY, String.valueOf(imageSize) + " bytes");
            res.setStatus(HttpServletResponse.SC_OK);
            res.getWriter().write(new Gson().toJson(imageMetaData));
        } catch (Exception e) {
            ErrorMsg errorMsg = new ErrorMsg("Error processing the request: " + e.getMessage());
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            res.getWriter().write(new Gson().toJson(errorMsg));
        }

    }

    private boolean isUrlValid(String[] urlPaths, String methodType) {
        if (GET.equalsIgnoreCase(methodType)) {
            // Validating /albums/{albumID}
            if (urlPaths.length != 2) {
                return false;
            }
            try {
                Integer.parseInt(urlPaths[1]);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private int getImageSize(String image, int base64StringLength) {
        int padding = 0;
        if (image.endsWith("==")) {
            padding = 2;
        } else if (image.endsWith("=")) {
            padding = 1;
        }
        int imageSize = (3 * (base64StringLength / 4)) - padding;
        return imageSize;
    }
}
