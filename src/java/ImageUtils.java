package com.tiho.common.utils;


import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.BASE64Decoder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * 图片工具类，包含图片的压缩
 * longyifan 2019.5.22 added
 */
public class ImageUtils {

    private final static Logger LOGGER = LoggerFactory.getLogger(ImageUtils.class);


    /**
     * 上传图片，并处理旋转和压缩
     *
     * @param in 文件流
     * @param saveFile
     * @param proportion 压缩等级
     * @return
     */
    public static boolean compressAndUpload(InputStream in, File saveFile, int proportion) {

        if (null == in || null == saveFile || proportion < 1) {// 检查参数有效性
            return false;
        }

        ByteArrayOutputStream baos = cloneInputStream(in);

        // 打开两个新的输入流 并转换为base64编码
        InputStream stream1 =getBase64FromInputStream (new ByteArrayInputStream(baos.toByteArray())) ;
        InputStream stream2 =getBase64FromInputStream(new ByteArrayInputStream(baos.toByteArray()));


        BufferedImage srcImage = null;
        try {
            srcImage = ImageIO.read(stream1);
        } catch (IOException e) {
            LOGGER.error("流转成文件失败！", e);
            return false;
        }

        //判断图片是否旋转 如过旋转先处理选装
        int ro = getRotateAngleForPhoto(stream2);

        BufferedImage angelSrcImage = null;
        Integer compressWith = null;
        Integer compressHeight = null;
        if (ro > 0) { //需要翻转
            //angelSrcImage = getAngelBufferedImg(srcImage, srcImage.getWidth(), srcImage.getHeight(), ro);
            angelSrcImage= RotateImageUtils.Rotate(srcImage,ro);
            compressWith = angelSrcImage.getWidth() / proportion;
            compressHeight = angelSrcImage.getHeight() / proportion;
        } else {
            compressWith = srcImage.getWidth() / proportion;
            compressHeight = srcImage.getHeight() / proportion;
        }

        //压缩图片
        srcImage = resize(angelSrcImage != null ? angelSrcImage : srcImage, compressWith, compressHeight);

        // 缩放后的图像的宽和高
        int w = srcImage.getWidth();
        int h = srcImage.getHeight();
        // 如果缩放后的图像和要求的图像宽度一样，就对缩放的图像的高度进行截取
        if (w == compressWith) {
            // 计算X轴坐标
            int x = 0;
            int y = h / 2 - compressHeight / 2;
            try {
                saveSubImage(srcImage, new Rectangle(x, y, compressWith, compressHeight), saveFile);
            } catch (IOException e) {
                LOGGER.error("保存压缩过的图片失败！", e);
                return false;
            }
        } else if (h == compressHeight) {  // 否则如果是缩放后的图像的高度和要求的图像高度一样，就对缩放后的图像的宽度进行截取
            // 计算X轴坐标
            int x = w / 2 - compressWith / 2;
            int y = 0;
            try {
                saveSubImage(srcImage, new Rectangle(x, y, compressWith, compressHeight), saveFile);
            } catch (IOException e) {
                LOGGER.error("保存压缩过的图片失败！", e);
                return false;
            }
        }
        return true;
    }


    /**
     * 实现图像的等比缩放
     *
     * @param source
     * @param targetW
     * @param targetH
     * @return
     */
    private static BufferedImage resize(BufferedImage source, int targetW, int targetH) {

        // targetW，targetH分别表示目标长和宽
        int type = source.getType();
        BufferedImage target = null;
        double sx = (double) targetW / source.getWidth();
        double sy = (double) targetH / source.getHeight();
        // 这里想实现在targetW，targetH范围内实现等比缩放。如果不需要等比缩放
        // 则将下面的if else语句注释即可
        if (sx < sy) {
            sx = sy;
            targetW = (int) (sx * source.getWidth());
        } else {
            sy = sx;
            targetH = (int) (sy * source.getHeight());
        }

        target = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_BGR);

        Graphics2D g = target.createGraphics();
        // smoother than exlax:
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawRenderedImage(source, AffineTransform.getScaleInstance(sx, sy));
        g.dispose();
        return target;
    }

    /**
     * 实现缩放后的截图
     *
     * @param image          缩放后的图像
     * @param subImageBounds 要截取的子图的范围
     * @param subImageFile   要保存的文件
     * @throws IOException
     */
    private static void saveSubImage(BufferedImage image,
                                     Rectangle subImageBounds,
                                     File subImageFile) throws IOException {
        if (subImageBounds.x < 0 || subImageBounds.y < 0
                || subImageBounds.width - subImageBounds.x > image.getWidth()
                || subImageBounds.height - subImageBounds.y > image.getHeight()) {
            return;
        }
        BufferedImage subImage = image.getSubimage(subImageBounds.x, subImageBounds.y, subImageBounds.width, subImageBounds.height);
        String fileName = subImageFile.getName();
        String formatName = fileName.substring(fileName.lastIndexOf('.') + 1);
        ImageIO.write(subImage, formatName, subImageFile);
    }


    /**
     * 获得翻转后的图片流
     *
     * @param src
     * @param width
     * @param height
     * @param ro
     * @return
     */
    public static BufferedImage getAngelBufferedImg(BufferedImage src, int width, int height, int ro) {

        int angle = (int) (90 * ro);
        int type = src.getColorModel().getTransparency();
        int wid = width;
        int hei = height;


        int src_width = src.getWidth(null);
        int src_height = src.getHeight(null);
        Rectangle rect_des = calcRotatedSize(new Rectangle(new Dimension(src_width, src_height)), angle);

        BufferedImage BfImg = new BufferedImage(rect_des.width, rect_des.height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2 = BfImg.createGraphics();
        g2.translate((rect_des.width - src_width) / 2, (rect_des.height - src_height) / 2);
        g2.rotate(Math.toRadians(angle), src_width / 2, src_height / 2);
        g2.drawImage(src, null, null);
        g2.dispose();
        return BfImg;
    }


    /**
     * 计算旋转参数
     */
    public static Rectangle calcRotatedSize(Rectangle src, int angel) {
        // if angel is greater than 90 degree,we need to do some conversion.
        if (angel > 90) {
            if (angel / 9 % 2 == 1) {
                int temp = src.height;
                src.height = src.width;
                src.width = temp;
            }
            angel = angel % 90;
        }

        double r = Math.sqrt(src.height * src.height + src.width * src.width) / 2;
        double len = 2 * Math.sin(Math.toRadians(angel) / 2) * r;
        double angel_alpha = (Math.PI - Math.toRadians(angel)) / 2;
        double angel_dalta_width = Math.atan((double) src.height / src.width);
        double angel_dalta_height = Math.atan((double) src.width / src.height);

        int len_dalta_width = (int) (len * Math.cos(Math.PI - angel_alpha - angel_dalta_width));
        int len_dalta_height = (int) (len * Math.cos(Math.PI - angel_alpha - angel_dalta_height));
        int des_width = src.width + len_dalta_width * 2;
        int des_height = src.height + len_dalta_height * 2;
        return new java.awt.Rectangle(new Dimension(des_width, des_height));
    }


    //获取图形exif
    public static Map<String, Object> getExif(InputStream ins) {
        Map<String, Object> map = new HashMap<String, Object>();
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(ins);
            map = getExifMap(metadata);
        } catch (ImageProcessingException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return map;
    }

    //获取exif信息，将旋转角度信息拿到
    private static Map<String, Object> getExifMap(Metadata metadata) {
        Map<String, Object> map = new HashMap<String, Object>();
        String tagName = null;
        String desc = null;
        for (Directory directory : metadata.getDirectories()) {
            for (Tag tag : directory.getTags()) {
                tagName = tag.getTagName();
                desc = tag.getDescription();
                /*  System.out.println(tagName+","+desc);*/
                if (tagName.equals("Orientation")) {
                    map.put("Orientation", desc);
                    break;
                }
            }
        }
        return map;
    }

    //获取旋转角度
    public static int getAngle(Map<String, Object> map) {
        if (map == null || map.size() == 0) {
            return 0;
        }
        String ori = map.get("Orientation").toString();
        int ro = 0;
        if (ori.indexOf("90") >= 0) {
            ro = 1;
        } else if (ori.indexOf("180") >= 0) {
            ro = 2;
        } else if (ori.indexOf("270") >= 0) {
            ro = 3;
        }
        return ro;
    }


    //复制流到字节数组
    private static ByteArrayOutputStream cloneInputStream(InputStream input) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = input.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            return baos;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    public static int getRotateAngleForPhoto(InputStream ins) {
        int angel = 0;
        try {
            //核心对象操作对象
            Metadata metadata = ImageMetadataReader.readMetadata(ins);
            //获取所有不同类型的Directory，如ExifSubIFDDirectory, ExifInteropDirectory, ExifThumbnailDirectory等，这些类均为ExifDirectoryBase extends Directory子类
            //分别遍历每一个Directory，根据Directory的Tags就可以读取到相应的信息
            int orientation = 0;
            Iterable<Directory> iterable = metadata.getDirectories();
            for (Iterator<Directory> iter = iterable.iterator(); iter.hasNext(); ) {
                Directory dr = iter.next();
                if (dr.getString(ExifIFD0Directory.TAG_ORIENTATION) != null) {
                    orientation = dr.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                }
                /*Collection<Tag> tags = dr.getTags();
                for (Tag tag : tags) {
               System.out.println(tag.getTagName() + "： " + tag.getDescription());
            }*/
            }
            if (orientation == 0 || orientation == 1) {
                angel = 360;
            } else if (orientation == 3) {
                angel = 180;
            } else if (orientation == 6) {
                angel = 90;
            } else if (orientation == 8) {
                angel = 270;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return angel;
    }

    public static ByteArrayInputStream getBase64FromInputStream(InputStream in) {
        // 将图片文件转化为字节数组字符串，并对其进行Base64编码处理
        byte[] data = null;
        // 读取图片字节数组
        try {
            ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int rc = 0;
            while ((rc = in.read(buff, 0, 100)) > 0) {
                swapStream.write(buff, 0, rc);
            }
            data = swapStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        ByteArrayInputStream stream = null;
        try {
            BASE64Decoder decoder = new BASE64Decoder();
            byte[] bytes1 = decoder.decodeBuffer(new String(Base64.encodeBase64(data)));
            LOGGER.info(new String(Base64.encodeBase64(data)));
            stream = new ByteArrayInputStream(bytes1);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
        return stream;
    }





    public static void main(String[] args) throws Exception {
        InputStream in = null;
        //缩放后需要保存的路径
        File saveFile = new File("d:\\image\\123-final.jpg");
        try {
            //原图片的路径
            in = new FileInputStream(new File("d:\\image\\org-windows.jpg"));
            if (compressAndUpload(in, saveFile, 4)) {
                System.out.println("图片压缩！");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            in.close();
        }

    }
}