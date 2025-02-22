package btools.routingapp;

import android.graphics.Point;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import btools.router.OsmNodeNamed;
import btools.router.OsmNogoPolygon;

/**
 * Read coordinates from a gpx-file
 */
public class CoordinateReaderInternal extends CoordinateReader
{
  private String internalDir;

  public CoordinateReaderInternal(String basedir )
  {
    this( basedir, false );
  }

  public CoordinateReaderInternal(String basedir, boolean shortPath )
  {
    super( basedir );
    if ( shortPath )
    {
      internalDir = basedir;
      tracksdir = "/tracks";
      rootdir = "";
    }
    else
    {
      internalDir = basedir + "/import";
      tracksdir = "/import/tracks";
      rootdir = "/import";
    }
  }

  @Override
  public long getTimeStamp() throws Exception
  {
    long t1 = new File( internalDir + "/favourites_bak.gpx" ).lastModified();
    long t2 = new File( internalDir + "/favourites.gpx" ).lastModified();
    return t1 > t2 ? t1 : t2;
  }

  @Override
  public int getTurnInstructionMode()
  {
    return 4; // comment style
  }

  /*
   * read the from and to position from a gpx-file
   * (with hardcoded name for now)
   */
  @Override
  public void readPointmap() throws Exception
  {
    if (! _readPointmap( internalDir + "/favourites_bak.gpx" ) ) {
        _readPointmap( internalDir + "/favourites.gpx" );
    }

    try
    {
      _readNogoLines( basedir+tracksdir );
    }
    catch( IOException ioe )
    {    
    }
  }

  private boolean _readPointmap( String filename ) throws Exception
  {
    BufferedReader br = null;
    try {
      br = new BufferedReader(
                           new InputStreamReader(
                            new FileInputStream( filename ) ) );
    } catch (FileNotFoundException e) {
      // ignore until it's reading error
      return false;
    }
    OsmNodeNamed n = null;

      for(;;)
      {
        String line = br.readLine();
        if ( line == null ) break;

        int idx0 = line.indexOf( " lat=\"" );
        int idx10 = line.indexOf( "<name>" );
        if ( idx0 >= 0 )
        {
          n = new OsmNodeNamed();
          idx0 += 6;
          int idx1 = line.indexOf( '"', idx0 );
          n.ilat = (int)( (Double.parseDouble( line.substring( idx0, idx1 ) ) + 90. )*1000000. + 0.5);
          int idx2 = line.indexOf( " lon=\"" );
          if ( idx2 < 0 ) continue;
          idx2 += 6;
          int idx3 = line.indexOf( '"', idx2 );
          n.ilon = (int)( ( Double.parseDouble( line.substring( idx2, idx3 ) ) + 180. )*1000000. + 0.5);
          if ( idx3 < 0 ) continue;
        }
        if ( n != null && idx10 >= 0 )
        {
          idx10 += 6;
          int idx11 = line.indexOf( "</name>", idx10 );
          if ( idx11 >= 0 )
          {
            n.name = line.substring( idx10, idx11 ).trim();
            checkAddPoint( "(one-for-all)", n );
          }
        }
      }
      br.close();
      return true;
  }
  
  private void _readNogoLines( String dirname ) throws IOException
  {
    
    File dir = new File( dirname );
    
    if (dir.exists() && dir.isDirectory())
    {
      for (final File file : dir.listFiles())
      {
        final String name = file.getName();
        if (name.startsWith("nogo") && name.endsWith(".gpx"))
        {
          try
          {
            _readNogoLine(file);
          }
          catch (Exception e)
          {
          }
        }
      }
    }
  }
  
  private void _readNogoLine( File file ) throws Exception
  {
    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
    factory.setNamespaceAware(false);
    XmlPullParser xpp = factory.newPullParser();

    xpp.setInput(new FileReader(file));

    List<Point> tmpPts = new ArrayList<Point>();
    int eventType = xpp.getEventType();
    int numSeg = 0;
    while (eventType != XmlPullParser.END_DOCUMENT) {
      switch(eventType) {
      case XmlPullParser.START_TAG: {
        if (xpp.getName().equals("trkpt") || xpp.getName().equals("rtept")) {
          final String lon = xpp.getAttributeValue(null,"lon");
          final String lat = xpp.getAttributeValue(null,"lat");
          if (lon != null && lat != null) {
            tmpPts.add(new Point(
                (int)( ( Double.parseDouble(lon) + 180. ) *1000000. + 0.5),
                (int)( ( Double.parseDouble(lat) +  90. ) *1000000. + 0.5)) );
          }
        }
        break;
      }
      case XmlPullParser.END_TAG: {
        if (xpp.getName().equals("trkseg") || xpp.getName().equals("rte")) { // rte has no segment
          OsmNogoPolygon nogo = null;
          if (tmpPts.size() >= 0) {
            if (tmpPts.get(0).x == tmpPts.get(tmpPts.size()-1).x &&
                tmpPts.get(0).y == tmpPts.get(tmpPts.size()-1).y) {
               nogo = new OsmNogoPolygon(true);
            } else {
              nogo = new OsmNogoPolygon(false);
            }
            for (Point p : tmpPts) {
              nogo.addVertex(p.x, p.y);
            }
            nogo.calcBoundingCircle();
            final String name = file.getName();
            nogo.name = name.substring(0, name.length() - 4);
            if (numSeg > 0) {
              nogo.name += Integer.toString(numSeg + 1);
            }
            numSeg++;
            checkAddPoint("(one-for-all)", nogo);
          }
          tmpPts.clear();
        }
        break;
      }
      }
      eventType = xpp.next();
    }
  }
}
