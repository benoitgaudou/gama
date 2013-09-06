/**
 *  SAVE_AGENT2SQL
 *  Author: Truong Minh Thai (thai.truongminh@gmail.com)
 */

model agent2DB_MSSQL 
  
global { 
	file buildingsShp <- file('../../includes/building.shp');
	file boundsShp <- file('../../includes/bounds.shp');

	map<string,string> PARAMS <- ['host'::'127.0.0.1','dbtype'::'sqlserver','database'::'spatial_DB','port'::'1433','user'::'sa','passwd'::'tmt'];

	init {
		create buildings from: buildingsShp with: [type::string(read ('NATURE'))];
		create bounds from: boundsShp;
		
		create species: DB_Accessor number: 1  
		{ 			
			do executeUpdate params: PARAMS updateComm: "DELETE FROM buildings";	
			do executeUpdate params: PARAMS updateComm: "DELETE FROM bounds";
		}
		write "Click on <<Step>> button to save data of agents to DB";		 
	}
}   

environment bounds: boundsShp ;

entities {   
	species DB_Accessor skills: [SQLSKILL] ;   
	species bounds {
		reflex printdata{
			 write ' name : ' + (name) ;
		}
		
		reflex savetosql{  // save data into MySQL
			write "begin"+ name;
			ask DB_Accessor {
				do insert params: PARAMS into: "bounds"
						  columns: ["geom"]
						  values: [myself.shape]
						  transform: true;   //Default is false. Transform Geometry GAMA type to  Geometry DB type
			}
		    write "finish "+ name;
		}		
	}
	species buildings {
		string type;
		
		reflex printdata{
			 write ' name : ' + (name) + '; type: ' + (type) + "shape:" + shape;
		}
		
		reflex savetosql{  // save data into SQLite
			write "begin"+ name;
			ask DB_Accessor {
				do insert params: PARAMS into: "buildings"
						  columns: ["name", "type","geom"]
						  values: [myself.name,myself.type,myself.shape]
						  transform: true;   //Default is false. Transform Geometry GAMA type to  Geometry DB type
			}
		    write "finish "+ name;
		}	
		
		aspect default {
			draw shape color: rgb('gray') ;
		}
	}
}      

experiment default_expr type: gui {
	output {
		
		display GlobalView {
			species buildings aspect: default;
		}
	}
}
